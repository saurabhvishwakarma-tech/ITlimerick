import { useState } from 'react';
import { Link } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/http';
import type { EventView, ParticipantView, TeamAdminView, VotingView } from '../../api/types';
import { Empty, ErrorState, Loading, Notice } from '../../components/ui';
import { formatDateTime } from '../../lib/time';
import { EditDialog, type FieldDef, type FormValues } from './CrudTab';

const teamFields: FieldDef[] = [
  { name: 'name', label: 'Team name', type: 'text', required: true },
  { name: 'projectName', label: 'Project name', type: 'text' },
  { name: 'description', label: 'Project description', type: 'textarea' },
  { name: 'sector', label: 'Sector', type: 'text' },
  { name: 'logoUrl', label: 'Logo link', type: 'url' },
  { name: 'projectUrl', label: 'Project link', type: 'url' },
  { name: 'shortlisted', label: 'Shortlisted', type: 'checkbox', hint: 'If any team is shortlisted, only shortlisted teams are on the voting ballot.' },
];

function teamForm(t?: TeamAdminView): FormValues {
  return {
    name: t?.name ?? '',
    projectName: t?.projectName ?? '',
    description: t?.description ?? '',
    sector: t?.sector ?? '',
    logoUrl: t?.logoUrl ?? '',
    projectUrl: t?.projectUrl ?? '',
    shortlisted: t?.shortlisted ?? false,
  };
}

export function HackathonTab({ base, event }: { base: string; event: EventView }) {
  const client = useQueryClient();
  const teams = useQuery({ queryKey: ['admin', `${base}/teams`], queryFn: () => api<TeamAdminView[]>(`${base}/teams`) });
  const participants = useQuery({
    queryKey: ['admin', `${base}/participants`],
    queryFn: () => api<ParticipantView[]>(`${base}/participants`),
  });
  const votes = useQuery({
    queryKey: ['admin', `${base}/votes`],
    queryFn: () => api<VotingView>(`${base}/votes`),
    refetchInterval: 15_000,
  });
  const [message, setMessage] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null);
  const [editing, setEditing] = useState<TeamAdminView | 'new' | null>(null);
  const [teamSize, setTeamSize] = useState(event.teamMaxSize);

  const refresh = () => {
    client.invalidateQueries({ queryKey: ['admin', `${base}/teams`] });
    client.invalidateQueries({ queryKey: ['admin', `${base}/participants`] });
    client.invalidateQueries({ queryKey: ['admin', `${base}/votes`] });
    client.invalidateQueries({ queryKey: ['teams', event.slug] });
  };
  const fail = (e: unknown) => setMessage({ kind: 'error', text: e instanceof Error ? e.message : 'That didn’t work.' });

  const sort = useMutation({
    mutationFn: () =>
      api<{ assigned: number; teamsCreated: number; teamsFilled: number }>(`${base}/teams/auto-sort`, {
        method: 'POST',
        body: { teamSize },
      }),
    onSuccess: (r) => {
      refresh();
      setMessage({
        kind: 'ok',
        text:
          r.assigned === 0
            ? 'Nobody is waiting for a team.'
            : `Placed ${r.assigned} ${r.assigned === 1 ? 'person' : 'people'}: topped up ${r.teamsFilled} existing ${r.teamsFilled === 1 ? 'team' : 'teams'} and created ${r.teamsCreated} new.`,
      });
    },
    onError: fail,
  });

  const move = useMutation({
    mutationFn: ({ teamId, userId }: { teamId: string; userId: string }) =>
      api(`${base}/teams/${teamId}/members`, { method: 'POST', body: { userId } }),
    onSuccess: () => {
      refresh();
      setMessage({ kind: 'ok', text: 'Moved.' });
    },
    onError: fail,
  });

  const removeMember = useMutation({
    mutationFn: ({ teamId, userId }: { teamId: string; userId: string }) =>
      api(`${base}/teams/${teamId}/members/${userId}`, { method: 'DELETE' }),
    onSuccess: () => {
      refresh();
      setMessage({ kind: 'ok', text: 'Removed from the team. They are back on the waiting list.' });
    },
    onError: fail,
  });

  const deleteTeam = useMutation({
    mutationFn: (teamId: string) => api(`${base}/teams/${teamId}`, { method: 'DELETE' }),
    onSuccess: () => {
      refresh();
      setMessage({ kind: 'ok', text: 'Team deleted. Its members are back on the waiting list.' });
    },
    onError: fail,
  });

  if (!event.hackathonEnabled) {
    return (
      <Empty title="The hackathon is switched off">
        <p>Turn it on in Event details to show Teams and Vote to attendees.</p>
        <Link className="btn" to={`/admin/events/${event.id}/details`}>
          Open event details
        </Link>
      </Empty>
    );
  }
  if (teams.isPending || participants.isPending) return <Loading />;
  if (teams.isError) return <ErrorState error={teams.error} onRetry={() => teams.refetch()} />;
  if (participants.isError) return <ErrorState error={participants.error} onRetry={() => participants.refetch()} />;

  const waiting = participants.data.filter((p) => p.lookingForTeam && !p.teamId);
  const unplaced = participants.data.filter((p) => !p.teamId);

  return (
    <div className="stack-l">
      {message && <Notice kind={message.kind}>{message.text}</Notice>}

      <section className="panel">
        <h2>Team sorter</h2>
        <p className="muted">
          {waiting.length === 0
            ? 'Nobody is waiting for a team right now.'
            : `${waiting.length} ${waiting.length === 1 ? 'person is' : 'people are'} waiting for a team.`}{' '}
          The sorter shuffles them, tops up teams that have space, then creates new teams of even size.
        </p>
        <div className="row">
          <label className="row" style={{ gap: '0.5rem' }}>
            <span className="field-label">Team size</span>
            <input
              type="number"
              min={2}
              max={50}
              value={teamSize}
              onChange={(e) => setTeamSize(Number(e.target.value))}
              style={{ width: '5.5rem' }}
            />
          </label>
          <button
            className="btn"
            disabled={sort.isPending || waiting.length === 0 || teamSize < 2}
            onClick={() => window.confirm(`Place ${waiting.length} people into teams of up to ${teamSize}?`) && sort.mutate()}
          >
            {sort.isPending ? 'Sorting…' : 'Sort people into teams'}
          </button>
        </div>
      </section>

      <section className="stack">
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <h2>Teams ({teams.data.length})</h2>
          <button className="btn" onClick={() => setEditing('new')}>
            Add team
          </button>
        </div>
        {teams.data.length === 0 ? (
          <Empty title="No teams yet">
            <p>Participants can create teams themselves, or you can add them here or use the sorter.</p>
          </Empty>
        ) : (
          <ul className="team-grid">
            {teams.data.map((t) => (
              <li key={t.id} className="panel" style={{ alignContent: 'start' }}>
                <div className="row" style={{ justifyContent: 'space-between' }}>
                  <h3>{t.name}</h3>
                  {t.shortlisted && <span className="shortlisted">Shortlisted</span>}
                </div>
                {t.projectName && <p>{t.projectName}</p>}
                <p className="muted">
                  Code <strong>{t.joinCode}</strong>, {t.members.length} of {event.teamMaxSize}
                </p>
                <ul className="member-list">
                  {t.members.map((m) => (
                    <li key={m.userId}>
                      <span>
                        {m.displayName}
                        {m.role === 'LEAD' && <span className="muted"> (lead)</span>}
                      </span>
                      <button
                        className="btn link small-link"
                        onClick={() => removeMember.mutate({ teamId: t.id, userId: m.userId })}
                      >
                        Remove
                      </button>
                    </li>
                  ))}
                </ul>
                {unplaced.length > 0 && (
                  <select
                    aria-label={`Add someone to ${t.name}`}
                    value=""
                    onChange={(e) => e.target.value && move.mutate({ teamId: t.id, userId: e.target.value })}
                  >
                    <option value="">Add someone without a team…</option>
                    {unplaced.map((p) => (
                      <option key={p.userId} value={p.userId}>
                        {p.displayName}
                        {p.skills ? ` (${p.skills})` : ''}
                      </option>
                    ))}
                  </select>
                )}
                <div className="row">
                  <button className="btn small secondary" onClick={() => setEditing(t)}>
                    Edit
                  </button>
                  <button
                    className="btn small danger"
                    onClick={() => window.confirm(`Delete ${t.name}? Its members go back on the waiting list.`) && deleteTeam.mutate(t.id)}
                  >
                    Delete
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="stack">
        <h2>Participants ({participants.data.length})</h2>
        {participants.data.length === 0 ? (
          <p className="muted">Nobody has signed up for the hackathon yet. People join from the Teams page.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th scope="col">Name</th>
                  <th scope="col">Email</th>
                  <th scope="col">Skills</th>
                  <th scope="col">Team</th>
                  <th scope="col">Move to</th>
                </tr>
              </thead>
              <tbody>
                {participants.data.map((p) => (
                  <tr key={p.userId}>
                    <td>{p.displayName}</td>
                    <td>{p.email}</td>
                    <td>{p.skills ?? ''}</td>
                    <td>{p.teamName ?? (p.lookingForTeam ? <span className="status-pill is-draft">Waiting</span> : 'No team')}</td>
                    <td>
                      <select
                        aria-label={`Move ${p.displayName} to a team`}
                        value=""
                        onChange={(e) => e.target.value && move.mutate({ teamId: e.target.value, userId: p.userId })}
                      >
                        <option value="">Choose…</option>
                        {teams.data
                          .filter((t) => t.id !== p.teamId)
                          .map((t) => (
                            <option key={t.id} value={t.id}>
                              {t.name}
                            </option>
                          ))}
                      </select>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="panel">
        <h2>Voting</h2>
        {votes.data && (
          <>
            <p className="muted">
              {votes.data.status === 'NOT_SCHEDULED' && 'Set the voting times in Event details to open voting.'}
              {votes.data.status === 'NOT_OPEN' && votes.data.opensAt && `Opens ${formatDateTime(votes.data.opensAt, event.timezone)}.`}
              {votes.data.status === 'OPEN' && votes.data.closesAt && `Open now, closes ${formatDateTime(votes.data.closesAt, event.timezone)}. Counts update every 15 seconds.`}
              {votes.data.status === 'CLOSED' &&
                (event.votingResultsPublic ? 'Closed. Results are visible to everyone.' : 'Closed. Results are only visible to organisers until you publish them in Event details.')}
            </p>
            {votes.data.results && votes.data.results.length > 0 ? (
              <ol className="results">
                {votes.data.results.map((r) => (
                  <li key={r.teamId} className="row" style={{ justifyContent: 'space-between' }}>
                    <span>{r.projectName || r.name}</span>
                    <strong>{r.votes}</strong>
                  </li>
                ))}
              </ol>
            ) : (
              <p>No votes yet.</p>
            )}
            <p className="muted">{votes.data.totalVotes} votes in total.</p>
          </>
        )}
      </section>

      {editing && (
        <EditDialog
          title={editing === 'new' ? 'Add team' : `Edit ${editing.name}`}
          fields={teamFields}
          initial={teamForm(editing === 'new' ? undefined : editing)}
          timezone={event.timezone}
          onClose={() => setEditing(null)}
          onSave={async (values) => {
            if (editing === 'new') await api(`${base}/teams`, { method: 'POST', body: values });
            else await api(`${base}/teams/${editing.id}`, { method: 'PUT', body: values });
            refresh();
            setEditing(null);
            setMessage({ kind: 'ok', text: editing === 'new' ? 'Team added.' : 'Team saved.' });
          }}
        />
      )}
    </div>
  );
}
