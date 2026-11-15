import { useEffect, useMemo } from 'react';
import { Link, useParams } from 'react-router';
import { useAgenda, useAnnouncements, useEvent, useSession, useSpeaker, useSpeakers, useSponsors } from '../api/queries';
import type { SponsorView } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { markUpdatesSeen } from '../components/Shell';
import { Avatar, BookmarkButton, Empty, ErrorState, Loading, safeUrl } from '../components/ui';
import { formatDateTime, formatDay, formatTime, relativeTime } from '../lib/time';
import { useNow } from '../lib/useNow';
import { useSchedule } from '../lib/useSchedule';

function useTz(slug: string) {
  return useEvent(slug).data?.timezone ?? 'Europe/Dublin';
}

// ---------------- Session ----------------

export function SessionPage() {
  const { slug = '', sessionId = '' } = useParams();
  const session = useSession(sessionId);
  const tz = useTz(slug);
  const schedule = useSchedule(slug);

  if (session.isPending) return <Loading />;
  if (session.isError) return <ErrorState error={session.error} onRetry={() => session.refetch()} />;
  const s = session.data;

  return (
    <div className="narrow stack-l">
      <Link className="back" to={`/e/${slug}`}>
        Back to the agenda
      </Link>
      <div className="stack">
        <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'nowrap' }}>
          <h1>{s.title}</h1>
          <BookmarkButton saved={schedule.ids.has(s.id)} title={s.title} onToggle={() => schedule.toggle(s.id)} />
        </div>
        <p className="event-meta">
          {formatDay(s.startsAt, tz)}, {formatTime(s.startsAt, tz)}
          {s.endsAt && ` to ${formatTime(s.endsAt, tz)}`}
          {s.room && `, ${s.room.name}`}
        </p>
        {s.status === 'CANCELLED' && <p className="cancelled">This session has been cancelled.</p>}
      </div>
      {s.description && <p className="prose">{s.description}</p>}
      {s.speakers.length > 0 && (
        <section className="stack">
          <h2>{s.speakers.length === 1 ? 'Speaker' : 'Speakers'}</h2>
          <ul className="speaker-grid">
            {s.speakers.map((sp) => (
              <li key={sp.id}>
                <Link className="speaker-tile" to={`/e/${slug}/speakers/${sp.id}`}>
                  <Avatar name={sp.name} url={sp.photoUrl} size="medium" />
                  <div>
                    <h3>{sp.name}</h3>
                    {sp.company && <p>{sp.company}</p>}
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

// ---------------- Speakers ----------------

export function SpeakersPage() {
  const { slug = '' } = useParams();
  const speakers = useSpeakers(slug);
  if (speakers.isPending) return <Loading />;
  if (speakers.isError && !speakers.data) return <ErrorState error={speakers.error} onRetry={() => speakers.refetch()} />;
  return (
    <>
      <div className="page-title">
        <h1>Speakers</h1>
      </div>
      {speakers.data.length === 0 ? (
        <Empty title="Speakers coming soon">
          <p>We'll introduce everyone here once the line-up is confirmed.</p>
        </Empty>
      ) : (
        <ul className="speaker-grid">
          {speakers.data.map((sp) => (
            <li key={sp.id}>
              <Link className="speaker-tile" to={`/e/${slug}/speakers/${sp.id}`}>
                <Avatar name={sp.name} url={sp.photoUrl} size="medium" />
                <div>
                  <h3>{sp.name}</h3>
                  {(sp.headline || sp.company) && <p>{[sp.headline, sp.company].filter(Boolean).join(', ')}</p>}
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

export function SpeakerPage() {
  const { slug = '', speakerId = '' } = useParams();
  const detail = useSpeaker(speakerId);
  const tz = useTz(slug);
  if (detail.isPending) return <Loading />;
  if (detail.isError) return <ErrorState error={detail.error} onRetry={() => detail.refetch()} />;
  const { speaker, sessions } = detail.data;
  const linkedin = safeUrl(speaker.linkedinUrl);
  const website = safeUrl(speaker.websiteUrl);
  return (
    <div className="narrow stack-l">
      <Link className="back" to={`/e/${slug}/speakers`}>
        All speakers
      </Link>
      <div className="profile-head">
        <Avatar name={speaker.name} url={speaker.photoUrl} size="large" />
        <div className="stack" style={{ gap: '0.3rem' }}>
          <h1>{speaker.name}</h1>
          {(speaker.headline || speaker.company) && (
            <p className="event-meta">{[speaker.headline, speaker.company].filter(Boolean).join(', ')}</p>
          )}
          {(linkedin || website) && (
            <p className="row">
              {linkedin && (
                <a href={linkedin} target="_blank" rel="noopener noreferrer">
                  LinkedIn
                </a>
              )}
              {website && (
                <a href={website} target="_blank" rel="noopener noreferrer">
                  Website
                </a>
              )}
            </p>
          )}
        </div>
      </div>
      {speaker.bio && <p className="prose">{speaker.bio}</p>}
      {sessions.length > 0 && (
        <section className="stack">
          <h2>Speaking at</h2>
          <ul className="feed">
            {sessions.map((s) => (
              <li key={s.id} className="post">
                <Link to={`/e/${slug}/sessions/${s.id}`}>
                  <h3>{s.title}</h3>
                </Link>
                <span className="muted">
                  {formatDateTime(s.startsAt, tz)}
                  {s.roomName && `, ${s.roomName}`}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}

// ---------------- Partners ----------------

export function PartnersPage() {
  const { slug = '' } = useParams();
  const sponsors = useSponsors(slug);
  const tiers = useMemo(() => {
    const groups = new Map<string, SponsorView[]>();
    (sponsors.data ?? []).forEach((s) => groups.set(s.tier, [...(groups.get(s.tier) ?? []), s]));
    return [...groups.entries()];
  }, [sponsors.data]);

  if (sponsors.isPending) return <Loading />;
  if (sponsors.isError && !sponsors.data) return <ErrorState error={sponsors.error} onRetry={() => sponsors.refetch()} />;
  return (
    <>
      <div className="page-title">
        <h1>Partners</h1>
        <p className="lede">The organisations that make this summit possible.</p>
      </div>
      {tiers.length === 0 ? (
        <Empty title="Partners coming soon" />
      ) : (
        tiers.map(([tier, list], i) => (
          <section key={tier} className={`tier ${i === 0 ? 'is-top' : ''}`}>
            <h2>{tier}</h2>
            <ul className="partner-grid">
              {list.map((p) => {
                const site = safeUrl(p.websiteUrl);
                const logo = safeUrl(p.logoUrl);
                return (
                  <li key={p.id}>
                    <div className="partner">
                      <div className="partner-logo">
                        {logo ? <img src={logo} alt={p.name} loading="lazy" /> : <span className="wordmark">{p.name}</span>}
                      </div>
                      {logo && <h3>{p.name}</h3>}
                      {p.description && <p>{p.description}</p>}
                      {site && (
                        <a href={site} target="_blank" rel="noopener noreferrer">
                          Visit {p.name}
                        </a>
                      )}
                    </div>
                  </li>
                );
              })}
            </ul>
          </section>
        ))
      )}
    </>
  );
}

// ---------------- Updates ----------------

export function UpdatesPage() {
  const { slug = '' } = useParams();
  const updates = useAnnouncements(slug);
  const tz = useTz(slug);
  const now = useNow(30_000);

  useEffect(() => {
    const latest = updates.data?.reduce<string | null>((m, a) => (!m || a.publishedAt > m ? a.publishedAt : m), null);
    if (latest) markUpdatesSeen(slug, latest);
  }, [updates.data, slug]);

  if (updates.isPending) return <Loading />;
  if (updates.isError && !updates.data) return <ErrorState error={updates.error} onRetry={() => updates.refetch()} />;
  return (
    <div className="narrow">
      <div className="page-title">
        <h1>Updates</h1>
        <p className="lede">Live news from the organisers. This page refreshes on its own.</p>
      </div>
      {updates.data.length === 0 ? (
        <Empty title="No updates yet">
          <p>Room changes, reminders and news will show up here during the event.</p>
        </Empty>
      ) : (
        <ol className="feed">
          {updates.data.map((a) => (
            <li key={a.id} className={`post ${a.priority === 'URGENT' ? 'is-urgent' : ''}`}>
              <h3>{a.title}</h3>
              {a.body && <p className="post-body">{a.body}</p>}
              <time dateTime={a.publishedAt} title={formatDateTime(a.publishedAt, tz)}>
                {a.pinned ? 'Pinned, ' : ''}
                {relativeTime(a.publishedAt, now)}
              </time>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

// ---------------- My schedule ----------------

export function MySchedulePage() {
  const { slug = '' } = useParams();
  const agenda = useAgenda(slug);
  const schedule = useSchedule(slug);
  const tz = useTz(slug);
  const { user } = useAuth();

  if (agenda.isPending) return <Loading />;
  if (agenda.isError && !agenda.data) return <ErrorState error={agenda.error} onRetry={() => agenda.refetch()} />;
  const saved = agenda.data.filter((s) => schedule.ids.has(s.id));

  return (
    <div className="narrow">
      <div className="page-title">
        <h1>My schedule</h1>
        <p className="lede">
          {user ? (
            'Saved to your account, so it follows you across devices.'
          ) : (
            <>
              Saved on this device only. <Link to="/login">Log in</Link> to keep it on all your devices.
            </>
          )}
        </p>
      </div>
      {saved.length === 0 ? (
        <Empty title="Nothing saved yet">
          <p>Tap the bookmark on any session in the agenda to add it here.</p>
          <Link className="btn" to={`/e/${slug}`}>
            Browse the agenda
          </Link>
        </Empty>
      ) : (
        <ol className="feed">
          {saved.map((s) => (
            <li key={s.id} className="post" style={{ gridTemplateColumns: '1fr auto' }}>
              <div className="stack" style={{ gap: '0.2rem' }}>
                <Link to={`/e/${slug}/sessions/${s.id}`}>
                  <h3>{s.title}</h3>
                </Link>
                <span className="muted">
                  {formatDateTime(s.startsAt, tz)}
                  {s.room && `, ${s.room.name}`}
                  {s.status === 'CANCELLED' && ', cancelled'}
                </span>
              </div>
              <BookmarkButton saved title={s.title} onToggle={() => schedule.toggle(s.id)} />
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
