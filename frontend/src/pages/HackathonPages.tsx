import { useMemo, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../api/http';
import { useEvent, useTeam, useTeams, useVoting } from '../api/queries';
import type { MyHackathonView, TeamView, VotingView } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Empty, ErrorState, Field, Loading, Notice, initials, safeUrl } from '../components/ui';
import { formatDateTime } from '../lib/time';

// ---------------- Shared bits ----------------

function TeamLogo({ team, size = 'medium' }: { team: { name: string; logoUrl: string | null }; size?: 'medium' | 'large' }) {
  const logo = safeUrl(team.logoUrl);
  return logo ? (
    <img className={`team-logo ${size}`} src={logo} alt="" loading="lazy" />
  ) : (
    <span className={`team-logo ${size}`} aria-hidden="true">
      {initials(team.name)}
    </span>
  );
}

function LoginPrompt({ action }: { action: string }) {
  const here = typeof window !== 'undefined' ? window.location.pathname : '/';
  return (
    <p>
      <Link to={`/login?next=${encodeURIComponent(here)}`}>Log in</Link> or{' '}
      <Link to={`/register?next=${encodeURIComponent(here)}`}>create an account</Link> to {action}.
    </p>
  );
}

function useMyHackathon(slug: string, enabled: boolean) {
  return useQuery({
    queryKey: ['me', 'hackathon', slug],
    queryFn: () => api<MyHackathonView>(`/api/v1/me/hackathon?event=${encodeURIComponent(slug)}`),
    enabled,
  });
}

// ---------------- Teams ----------------

export function TeamsPage() {
  const { slug = '' } = useParams();
  const { user } = useAuth();
  const event = useEvent(slug);
  const teams = useTeams(slug, !!user);
  const [query, setQuery] = useState('');
  const [sector, setSector] = useState<string | null>(null);

  const sectors = useMemo(
    () => [...new Set((teams.data ?? []).map((t) => t.sector).filter((s): s is string => !!s))].sort(),
    [teams.data],
  );

  if (event.isPending || teams.isPending) return <Loading />;
  if (event.isError) return <ErrorState error={event.error} />;
  if (!event.data.hackathonEnabled) return <Empty title="No hackathon at this event" />;
  if (teams.isError) return <ErrorState error={teams.error} onRetry={() => teams.refetch()} />;

  const q = query.trim().toLowerCase();
  const visible = teams.data.filter(
    (t) =>
      (!sector || t.sector === sector) &&
      (!q || [t.name, t.projectName, t.description].some((x) => x?.toLowerCase().includes(q))),
  );

  return (
    <div className="stack-l">
      <div className="page-title">
        <h1>Teams</h1>
        <p className="lede">The hackathon teams and what they're building.</p>
      </div>

      {user ? <MyTeamPanel slug={slug} /> : (
        <section className="panel">
          <h2>Taking part in the hackathon?</h2>
          <LoginPrompt action="create or join a team" />
        </section>
      )}

      <section className="stack">
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <h2>All teams ({teams.data.length})</h2>
        </div>
        {teams.data.length > 0 && (
          <>
            <input
              type="text"
              placeholder="Search by team or project"
              aria-label="Search teams"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
            {sectors.length > 1 && (
              <div className="agenda-controls" role="group" aria-label="Filter by sector">
                <button className="chip" aria-pressed={sector === null} onClick={() => setSector(null)}>
                  All sectors
                </button>
                {sectors.map((s) => (
                  <button key={s} className="chip" aria-pressed={sector === s} onClick={() => setSector(s)}>
                    {s}
                  </button>
                ))}
              </div>
            )}
          </>
        )}
        {teams.data.length === 0 ? (
          <Empty title="No teams yet">
            <p>Teams will appear here as people form them.</p>
          </Empty>
        ) : visible.length === 0 ? (
          <Empty title="No matches">
            <p>Try a different search or sector.</p>
          </Empty>
        ) : (
          <ul className="team-grid">
            {visible.map((t) => (
              <li key={t.id}>
                <TeamCard slug={slug} team={t} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function TeamCard({ slug, team }: { slug: string; team: TeamView }) {
  return (
    <Link className="team-card" to={`/e/${slug}/teams/${team.id}`}>
      <div className="team-card-head">
        <TeamLogo team={team} />
        <div>
          <h3>{team.projectName || team.name}</h3>
          {team.projectName && <p className="muted">{team.name}</p>}
        </div>
      </div>
      {team.description && <p className="team-desc">{team.description}</p>}
      <p className="session-sub">
        {team.shortlisted && <span className="shortlisted">Shortlisted</span>}
        {team.sector && <span>{team.sector}</span>}
        <span>
          {team.memberCount} {team.memberCount === 1 ? 'member' : 'members'}
        </span>
      </p>
    </Link>
  );
}

function MyTeamPanel({ slug }: { slug: string }) {
  const client = useQueryClient();
  const me = useMyHackathon(slug, true);
  const [mode, setMode] = useState<'none' | 'create' | 'join' | 'edit'>('none');
  const [error, setError] = useState<ApiError | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  const done = (view: MyHackathonView, message: string) => {
    client.setQueryData(['me', 'hackathon', slug], view);
    client.invalidateQueries({ queryKey: ['teams', slug] });
    client.invalidateQueries({ queryKey: ['voting', slug] });
    setMode('none');
    setError(null);
    setInfo(message);
  };
  const fail = (e: unknown) => setError(e instanceof ApiError ? e : new ApiError(0, null));

  const leave = useMutation({
    mutationFn: () => api<MyHackathonView>(`/api/v1/me/team/leave?event=${encodeURIComponent(slug)}`, { method: 'POST' }),
    onSuccess: (v) => done(v, 'You left the team.'),
    onError: fail,
  });

  if (me.isPending) return <Loading />;
  if (me.isError) return <ErrorState error={me.error} onRetry={() => me.refetch()} />;
  const m = me.data;

  return (
    <section className="panel my-team">
      {info && <Notice kind="ok">{info}</Notice>}
      {error && mode === 'none' && <Notice kind="error">{error.message}</Notice>}

      {m.team ? (
        <>
          <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div className="stack" style={{ gap: '0.25rem' }}>
              <span className="muted">Your team</span>
              <h2>{m.team.name}</h2>
              {m.team.projectName && <p>{m.team.projectName}</p>}
            </div>
            <TeamLogo team={m.team} />
          </div>
          <div className="join-code">
            <span className="muted">Share this code so teammates can join</span>
            <strong>{m.joinCode}</strong>
            <span className="muted">
              {m.team.memberCount} of {m.teamMaxSize} places taken: {m.team.memberNames.join(', ')}
            </span>
          </div>
          {mode === 'edit' ? (
            <TeamForm
              initial={m.team}
              submitLabel="Save project"
              error={error}
              onCancel={() => setMode('none')}
              onSubmit={async (body) => {
                try {
                  done(await api(`/api/v1/me/team?event=${encodeURIComponent(slug)}`, { method: 'PUT', body }), 'Project saved.');
                } catch (e) {
                  fail(e);
                }
              }}
            />
          ) : (
            <div className="row">
              <button className="btn" onClick={() => { setInfo(null); setMode('edit'); }}>
                Edit project details
              </button>
              <button
                className="btn secondary"
                onClick={() => window.confirm('Leave your team? You can join another one afterwards.') && leave.mutate()}
              >
                Leave team
              </button>
            </div>
          )}
        </>
      ) : (
        <>
          <h2>Your hackathon team</h2>
          {m.lookingForTeam ? (
            <p>You're on the list of people looking for a team. The organisers will place you, or you can start or join one now.</p>
          ) : (
            <p>Start a team and invite people with its code, join one with a code from a teammate, or ask the organisers to place you.</p>
          )}
          {mode === 'create' && (
            <TeamForm
              submitLabel="Create team"
              error={error}
              onCancel={() => setMode('none')}
              onSubmit={async (body) => {
                try {
                  done(await api(`/api/v1/me/team?event=${encodeURIComponent(slug)}`, { method: 'POST', body }), 'Team created. Share the code with your teammates.');
                } catch (e) {
                  fail(e);
                }
              }}
            />
          )}
          {mode === 'join' && (
            <JoinForm
              error={error}
              onCancel={() => setMode('none')}
              onSubmit={async (code) => {
                try {
                  done(await api('/api/v1/me/team/join', { method: 'POST', body: { code } }), "You've joined the team.");
                } catch (e) {
                  fail(e);
                }
              }}
            />
          )}
          {mode === 'none' && (
            <div className="row">
              <button className="btn" onClick={() => { setInfo(null); setError(null); setMode('create'); }}>
                Start a team
              </button>
              <button className="btn secondary" onClick={() => { setInfo(null); setError(null); setMode('join'); }}>
                Join with a code
              </button>
              <PoolToggle slug={slug} me={m} onDone={done} onError={fail} />
            </div>
          )}
        </>
      )}
    </section>
  );
}

function PoolToggle({
  slug,
  me,
  onDone,
  onError,
}: {
  slug: string;
  me: MyHackathonView;
  onDone: (v: MyHackathonView, msg: string) => void;
  onError: (e: unknown) => void;
}) {
  const [skills, setSkills] = useState(me.skills ?? '');
  const [open, setOpen] = useState(false);
  const save = async (looking: boolean) => {
    try {
      const v = await api<MyHackathonView>(`/api/v1/me/hackathon?event=${encodeURIComponent(slug)}`, {
        method: 'PUT',
        body: { skills, lookingForTeam: looking },
      });
      onDone(v, looking ? "You're on the list. The organisers will place you in a team." : "You're off the list.");
      setOpen(false);
    } catch (e) {
      onError(e);
    }
  };
  if (me.lookingForTeam) {
    return (
      <button className="btn link" onClick={() => save(false)}>
        Take me off the list
      </button>
    );
  }
  if (!open) {
    return (
      <button className="btn link" onClick={() => setOpen(true)}>
        Find me a team
      </button>
    );
  }
  return (
    <div className="stack" style={{ width: '100%' }}>
      <Field label="Your skills (optional)" hint="Helps organisers build balanced teams, e.g. React, data, design, pitching.">
        {(p) => <input {...p} type="text" maxLength={300} value={skills} onChange={(e) => setSkills(e.target.value)} />}
      </Field>
      <div className="row">
        <button className="btn" onClick={() => save(true)}>
          Put me on the list
        </button>
        <button className="btn secondary" onClick={() => setOpen(false)}>
          Cancel
        </button>
      </div>
    </div>
  );
}

type TeamBody = { name: string; projectName: string; description: string; sector: string; logoUrl: string; projectUrl: string };

function TeamForm({
  initial,
  submitLabel,
  error,
  onSubmit,
  onCancel,
}: {
  initial?: TeamView;
  submitLabel: string;
  error: ApiError | null;
  onSubmit: (body: TeamBody) => Promise<void>;
  onCancel: () => void;
}) {
  const [v, setV] = useState<TeamBody>({
    name: initial?.name ?? '',
    projectName: initial?.projectName ?? '',
    description: initial?.description ?? '',
    sector: initial?.sector ?? '',
    logoUrl: initial?.logoUrl ?? '',
    projectUrl: initial?.projectUrl ?? '',
  });
  const [busy, setBusy] = useState(false);
  const set = (k: keyof TeamBody) => (e: { target: { value: string } }) => setV({ ...v, [k]: e.target.value });
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    await onSubmit(v);
    setBusy(false);
  };
  return (
    <form className="form" onSubmit={submit} noValidate>
      {error && <Notice kind="error">{error.message}</Notice>}
      <Field label="Team name" error={error?.fields.name}>
        {(p) => <input {...p} type="text" maxLength={80} value={v.name} onChange={set('name')} />}
      </Field>
      <Field label="Project name (optional)" error={error?.fields.projectName}>
        {(p) => <input {...p} type="text" maxLength={150} value={v.projectName} onChange={set('projectName')} />}
      </Field>
      <Field label="What are you building? (optional)" error={error?.fields.description}>
        {(p) => <textarea {...p} maxLength={3000} value={v.description} onChange={set('description')} />}
      </Field>
      <Field label="Sector (optional)" hint="e.g. Health, Education, Climate." error={error?.fields.sector}>
        {(p) => <input {...p} type="text" maxLength={80} value={v.sector} onChange={set('sector')} />}
      </Field>
      <Field label="Logo link (optional)" hint="A full https:// link to an image." error={error?.fields.logoUrl}>
        {(p) => <input {...p} type="url" value={v.logoUrl} onChange={set('logoUrl')} />}
      </Field>
      <Field label="Project or demo link (optional)" error={error?.fields.projectUrl}>
        {(p) => <input {...p} type="url" value={v.projectUrl} onChange={set('projectUrl')} />}
      </Field>
      <div className="row">
        <button className="btn" disabled={busy || !v.name.trim()}>
          {busy ? 'Saving…' : submitLabel}
        </button>
        <button type="button" className="btn secondary" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  );
}

function JoinForm({
  error,
  onSubmit,
  onCancel,
}: {
  error: ApiError | null;
  onSubmit: (code: string) => Promise<void>;
  onCancel: () => void;
}) {
  const [code, setCode] = useState('');
  return (
    <form
      className="form"
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit(code);
      }}
      noValidate
    >
      {error && <Notice kind="error">{error.message}</Notice>}
      <Field label="Team code" hint="Six letters and numbers, from a teammate.">
        {(p) => (
          <input
            {...p}
            type="text"
            className="code-input"
            autoCapitalize="characters"
            autoComplete="off"
            maxLength={6}
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
          />
        )}
      </Field>
      <div className="row">
        <button className="btn" disabled={code.trim().length < 6}>
          Join team
        </button>
        <button type="button" className="btn secondary" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  );
}

export function TeamPage() {
  const { slug = '', teamId = '' } = useParams();
  const { user } = useAuth();
  const team = useTeam(teamId, !!user);
  if (team.isPending) return <Loading />;
  if (team.isError) return <ErrorState error={team.error} onRetry={() => team.refetch()} />;
  const t = team.data;
  const link = safeUrl(t.projectUrl);
  return (
    <div className="narrow stack-l">
      <Link className="back" to={`/e/${slug}/teams`}>
        All teams
      </Link>
      <div className="profile-head">
        <TeamLogo team={t} size="large" />
        <div className="stack" style={{ gap: '0.3rem' }}>
          <h1>{t.projectName || t.name}</h1>
          <p className="event-meta">
            {t.projectName ? t.name : null}
            {t.projectName && t.sector ? ', ' : null}
            {t.sector}
          </p>
          {t.shortlisted && <span className="shortlisted">Shortlisted</span>}
        </div>
      </div>
      {t.description && <p className="prose">{t.description}</p>}
      {link && (
        <p>
          <a className="btn secondary" href={link} target="_blank" rel="noopener noreferrer">
            Open the project
          </a>
        </p>
      )}
      <section className="stack">
        <h2>Team</h2>
        {user ? (
          t.memberNames.length ? (
            <p>{t.memberNames.join(', ')}</p>
          ) : (
            <p className="muted">No members yet.</p>
          )
        ) : (
          <>
            <p className="muted">
              {t.memberCount} {t.memberCount === 1 ? 'member' : 'members'}.
            </p>
            <LoginPrompt action="see who's on this team" />
          </>
        )}
      </section>
    </div>
  );
}

// ---------------- Voting ----------------

export function VotePage() {
  const { slug = '' } = useParams();
  const { user } = useAuth();
  const event = useEvent(slug);
  const voting = useVoting(slug, user?.id);
  const client = useQueryClient();
  const [error, setError] = useState<string | null>(null);

  const vote = useMutation({
    mutationFn: (teamId: string | null) =>
      teamId
        ? api<VotingView>(`/api/v1/me/vote?event=${encodeURIComponent(slug)}`, { method: 'PUT', body: { teamId } })
        : api<VotingView>(`/api/v1/me/vote?event=${encodeURIComponent(slug)}`, { method: 'DELETE' }),
    onSuccess: (v) => {
      client.setQueryData(['voting', slug, user?.id], v);
      setError(null);
    },
    onError: (e) => setError(e instanceof Error ? e.message : 'Your vote was not saved.'),
  });

  if (event.isPending || voting.isPending) return <Loading />;
  if (event.isError) return <ErrorState error={event.error} />;
  if (!event.data.hackathonEnabled) return <Empty title="No vote at this event" />;
  if (voting.isError) return <ErrorState error={voting.error} onRetry={() => voting.refetch()} />;

  const v = voting.data;
  const tz = event.data.timezone;
  const max = Math.max(1, ...(v.results ?? []).map((r) => r.votes));

  return (
    <div className="narrow stack-l">
      <div className="page-title">
        <h1>Vote</h1>
        <p className="lede">One vote each for the project of the day. You can change your vote until voting closes.</p>
      </div>

      <div className={`vote-status is-${v.status.toLowerCase()}`} role="status">
        {v.status === 'NOT_SCHEDULED' && 'Voting times will be announced on the day.'}
        {v.status === 'NOT_OPEN' && v.opensAt && <>Voting opens {formatDateTime(v.opensAt, tz)}.</>}
        {v.status === 'OPEN' && v.closesAt && <>Voting is open until {formatDateTime(v.closesAt, tz)}.</>}
        {v.status === 'CLOSED' && 'Voting has closed. The projects are still here to read.'}
      </div>

      {error && <Notice kind="error">{error}</Notice>}
      {v.status === 'OPEN' && !user && <LoginPrompt action="vote" />}

      {v.results && (
        <section className="stack">
          <h2>Results</h2>
          <ol className="results">
            {v.results.map((r, i) => (
              <li key={r.teamId}>
                <div className="row" style={{ justifyContent: 'space-between' }}>
                  <strong>
                    {i + 1}. {r.projectName || r.name}
                  </strong>
                  <span>
                    {r.votes} {r.votes === 1 ? 'vote' : 'votes'}
                  </span>
                </div>
                <div className="bar" aria-hidden="true">
                  <span style={{ width: `${(r.votes / max) * 100}%` }} />
                </div>
              </li>
            ))}
          </ol>
          <p className="muted">{v.totalVotes} votes in total.</p>
        </section>
      )}

      {v.candidates.length === 0 ? (
        <Empty title="No projects on the ballot yet" />
      ) : (
        <ul className="ballot">
          {v.candidates.map((c) => {
            const chosen = v.myVoteTeamId === c.teamId;
            return (
              <li key={c.teamId} className={`ballot-item ${chosen ? 'is-chosen' : ''}`}>
                <div className="team-card-head">
                  <TeamLogo team={{ name: c.name, logoUrl: c.logoUrl }} />
                  <div>
                    <h3>
                      <Link to={`/e/${slug}/teams/${c.teamId}`}>{c.projectName || c.name}</Link>
                    </h3>
                    {c.projectName && <p className="muted">{c.name}</p>}
                  </div>
                </div>
                {c.description && <p className="team-desc">{c.description}</p>}
                {v.status === 'OPEN' && user && (
                  <div className="row">
                    {c.isMyTeam ? (
                      <span className="muted">Your team</span>
                    ) : chosen ? (
                      <>
                        <span className="voted">Your vote</span>
                        <button className="btn link" onClick={() => vote.mutate(null)} disabled={vote.isPending}>
                          Remove my vote
                        </button>
                      </>
                    ) : (
                      <button className="btn" onClick={() => vote.mutate(c.teamId)} disabled={vote.isPending}>
                        {v.myVoteTeamId ? 'Change my vote to this' : 'Vote for this project'}
                      </button>
                    )}
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}

// ---------------- More (phones, hackathon events) ----------------

export function MorePage() {
  const { slug = '' } = useParams();
  const { user } = useAuth();
  const links = [
    { to: `/e/${slug}/speakers`, label: 'Speakers' },
    { to: `/e/${slug}/partners`, label: 'Partners' },
    { to: `/e/${slug}/my-schedule`, label: 'My schedule' },
    { to: user ? '/account' : '/login', label: user ? 'Your account' : 'Log in' },
    { to: '/privacy', label: 'Privacy notice' },
  ];
  return (
    <div className="narrow stack">
      <h1>More</h1>
      <ul className="more-list">
        {links.map((l) => (
          <li key={l.to}>
            <Link to={l.to}>{l.label}</Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
