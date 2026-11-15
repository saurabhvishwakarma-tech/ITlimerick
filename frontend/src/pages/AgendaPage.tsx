import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';
import { useAgenda, useAnnouncements, useEvent } from '../api/queries';
import type { EventView, SessionView } from '../api/types';
import { Avatar, BookmarkButton, Empty, ErrorState, Loading } from '../components/ui';
import { dayKey, formatDay, formatTime } from '../lib/time';
import { useNow } from '../lib/useNow';
import { useSchedule } from '../lib/useSchedule';

const TYPE_LABEL: Record<SessionView['type'], string> = {
  TALK: 'Talk',
  KEYNOTE: 'Keynote',
  PANEL: 'Panel',
  WORKSHOP: 'Workshop',
  BREAK: 'Break',
  NETWORKING: 'Networking',
  OTHER: '',
};

const QUIET_TYPES = new Set<SessionView['type']>(['BREAK', 'NETWORKING']);
const DEFAULT_LENGTH_MS = 30 * 60_000;

/** When a session effectively ends: its end time, else the next session's start, else 30 minutes. */
export function effectiveEnd(sessions: SessionView[], index: number): number {
  const s = sessions[index];
  if (s.endsAt) return new Date(s.endsAt).getTime();
  const start = new Date(s.startsAt).getTime();
  const next = sessions.slice(index + 1).find((x) => new Date(x.startsAt).getTime() > start);
  return next ? new Date(next.startsAt).getTime() : start + DEFAULT_LENGTH_MS;
}

export function EventHeader({ event }: { event: EventView }) {
  const when = event.startsAt ? formatDay(event.startsAt, event.timezone) : null;
  return (
    <div className="event-head">
      <h1>{event.name}</h1>
      {event.tagline && <p className="lede">{event.tagline}</p>}
      {(when || event.venueName) && (
        <p className="event-meta">
          {[when, event.venueName].filter(Boolean).join(', ')}
        </p>
      )}
    </div>
  );
}

function UrgentBanner({ slug }: { slug: string }) {
  const updates = useAnnouncements(slug);
  const now = useNow();
  const urgent = updates.data?.find(
    (a) => a.priority === 'URGENT' && now - new Date(a.publishedAt).getTime() < 3 * 60 * 60_000,
  );
  if (!urgent) return null;
  return (
    <div className="urgent" role="status">
      <strong>{urgent.title}</strong>
      {urgent.body && <span>{urgent.body}</span>}
      <Link to={`/e/${slug}/updates`}>All updates</Link>
    </div>
  );
}

export function AgendaPage() {
  const { slug = '' } = useParams();
  const event = useEvent(slug);
  const agenda = useAgenda(slug);
  const schedule = useSchedule(slug);
  const now = useNow();
  const [onlySaved, setOnlySaved] = useState(false);
  const [roomId, setRoomId] = useState<string | null>(null);
  const [day, setDay] = useState<string | null>(null);

  const tz = event.data?.timezone ?? 'Europe/Dublin';
  const sessions = useMemo(() => agenda.data ?? [], [agenda.data]);
  const days = useMemo(() => [...new Set(sessions.map((s) => dayKey(s.startsAt, tz)))], [sessions, tz]);
  const rooms = useMemo(() => {
    const map = new Map<string, NonNullable<SessionView['room']>>();
    sessions.forEach((s) => s.room && map.set(s.room.id, s.room));
    return [...map.values()].sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name));
  }, [sessions]);

  if (event.isPending || (agenda.isPending && !agenda.data)) return <Loading label="Loading the agenda…" />;
  if (event.isError) return <ErrorState error={event.error} onRetry={() => event.refetch()} />;
  if (agenda.isError && !agenda.data) return <ErrorState error={agenda.error} onRetry={() => agenda.refetch()} />;

  const todayKey = dayKey(new Date(now).toISOString(), tz);
  const activeDay = day ?? (days.includes(todayKey) ? todayKey : days[0]);
  const visible = sessions
    .map((s, i) => ({ s, end: effectiveEnd(sessions, i) }))
    .filter(({ s }) => days.length < 2 || dayKey(s.startsAt, tz) === activeDay)
    .filter(({ s }) => !roomId || !s.room || s.room.id === roomId)
    .filter(({ s }) => !onlySaved || schedule.ids.has(s.id));

  // Where the amber "now" line goes: before the first session that starts after now, during the day.
  const dayStart = visible.length ? new Date(visible[0].s.startsAt).getTime() : 0;
  const dayEnd = visible.length ? Math.max(...visible.map((v) => v.end)) : 0;
  const showNowLine = visible.length > 0 && now >= dayStart && now < dayEnd;
  const nowIndex = showNowLine ? visible.findIndex(({ s }) => new Date(s.startsAt).getTime() > now) : -1;
  const headingIso = sessions.find((s) => dayKey(s.startsAt, tz) === activeDay)?.startsAt;

  return (
    <>
      <EventHeader event={event.data} />
      <UrgentBanner slug={slug} />

      <div className="agenda-controls" role="group" aria-label="Filter the agenda">
        {days.length > 1 &&
          days.map((d) => {
            const first = sessions.find((s) => dayKey(s.startsAt, tz) === d)!;
            return (
              <button key={d} className="chip" aria-pressed={d === activeDay} onClick={() => setDay(d)}>
                {formatDay(first.startsAt, tz).split(' ').slice(0, 2).join(' ')}
              </button>
            );
          })}
        {rooms.length > 1 && (
          <>
            <button className="chip" aria-pressed={roomId === null} onClick={() => setRoomId(null)}>
              All rooms
            </button>
            {rooms.map((r) => (
              <button key={r.id} className="chip" aria-pressed={roomId === r.id} onClick={() => setRoomId(r.id)}>
                {r.name}
              </button>
            ))}
          </>
        )}
        <button className="chip" aria-pressed={onlySaved} onClick={() => setOnlySaved((v) => !v)}>
          Saved only{schedule.ids.size ? ` (${schedule.ids.size})` : ''}
        </button>
      </div>

      {agenda.isError && <p className="muted">Showing the last saved copy. We'll update it when you're back online.</p>}

      {headingIso && <h2 className="day-heading">{formatDay(headingIso, tz)}</h2>}

      {sessions.length === 0 ? (
        <Empty title="The agenda is on its way">
          <p>Talks and timings will appear here as soon as the organisers publish them.</p>
        </Empty>
      ) : visible.length === 0 ? (
        <Empty title={onlySaved ? 'Nothing saved yet' : 'Nothing here'}>
          <p>
            {onlySaved
              ? 'Tap the bookmark on any session to add it to your schedule.'
              : 'No sessions match these filters.'}
          </p>
        </Empty>
      ) : (
        <ol className="timeline">
          {visible.map(({ s, end }, i) => {
            const start = new Date(s.startsAt).getTime();
            const isNow = now >= start && now < end && s.status !== 'CANCELLED';
            const isPast = now >= end;
            const quiet = QUIET_TYPES.has(s.type);
            return (
              <li key={s.id}>
                {i === nowIndex && (
                  <div className="now-line" aria-label={`It's now ${formatTime(new Date(now).toISOString(), tz)}`}>
                    <span>{formatTime(new Date(now).toISOString(), tz)}</span>
                    <span />
                  </div>
                )}
                <div className={`slot ${isNow ? 'is-now' : ''} ${isPast ? 'is-past' : ''}`}>
                  <div className="slot-time">
                    {formatTime(s.startsAt, tz)}
                    {s.endsAt && <small>to {formatTime(s.endsAt, tz)}</small>}
                  </div>
                  <div className="slot-body">
                    <article className={`session-card ${quiet ? 'is-break' : ''}`}>
                      <h3>
                        {isNow && <span className="now-flag">On now</span>}
                        <Link to={`/e/${slug}/sessions/${s.id}`}>{s.title}</Link>
                      </h3>
                      {!quiet && (
                        <BookmarkButton saved={schedule.ids.has(s.id)} title={s.title} onToggle={() => schedule.toggle(s.id)} />
                      )}
                      <p className="session-sub">
                        {s.status === 'CANCELLED' && <span className="cancelled">Cancelled</span>}
                        {TYPE_LABEL[s.type] && !quiet && <span className="type-tag">{TYPE_LABEL[s.type]}</span>}
                        {s.room && <span>{s.room.name}</span>}
                      </p>
                      {s.speakers.length > 0 && (
                        <div className="speaker-line">
                          {s.speakers.map((sp) => (
                            <Link key={sp.id} className="person-chip" to={`/e/${slug}/speakers/${sp.id}`}>
                              <Avatar name={sp.name} url={sp.photoUrl} />
                              <span>
                                {sp.name}
                                {sp.company && <span className="muted">, {sp.company}</span>}
                              </span>
                            </Link>
                          ))}
                        </div>
                      )}
                    </article>
                  </div>
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </>
  );
}
