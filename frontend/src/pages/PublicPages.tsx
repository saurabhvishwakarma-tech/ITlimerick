import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/http';
import { useAgenda, useAnnouncements, useChat, useEvent, useSession, useSpeaker, useSpeakers, useSponsors } from '../api/queries';
import type { AnnouncementView, ChatMessageView, Emoji, SponsorView } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { markUpdatesSeen } from '../components/Shell';
import { Avatar, BookmarkButton, Empty, ErrorState, Loading, Notice, safeUrl } from '../components/ui';
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

// ---------------- Updates (announcements + optional chat) ----------------

const EMOJI: { key: Emoji; char: string; label: string }[] = [
  { key: 'HEART', char: '\u2764\uFE0F', label: 'Love' },
  { key: 'THUMBS_UP', char: '\uD83D\uDC4D', label: 'Thumbs up' },
  { key: 'PARTY', char: '\uD83C\uDF89', label: 'Celebrate' },
  { key: 'CLAP', char: '\uD83D\uDC4F', label: 'Applause' },
];

function Reactions({ slug, a }: { slug: string; a: AnnouncementView }) {
  const { user } = useAuth();
  const client = useQueryClient();
  const navigate = useNavigate();
  const toggle = useMutation({
    mutationFn: ({ emoji, on }: { emoji: Emoji; on: boolean }) =>
      api<void>(`/api/v1/me/announcements/${a.id}/reactions/${emoji}`, { method: on ? 'PUT' : 'DELETE' }),
    onMutate: ({ emoji, on }) => {
      client.setQueryData<AnnouncementView[]>(['announcements', slug], (list) =>
        list?.map((x) =>
          x.id !== a.id
            ? x
            : {
                ...x,
                myReactions: on ? [...x.myReactions, emoji] : x.myReactions.filter((e) => e !== emoji),
                reactions: { ...x.reactions, [emoji]: Math.max(0, (x.reactions[emoji] ?? 0) + (on ? 1 : -1)) },
              },
        ),
      );
    },
    onSettled: () => client.invalidateQueries({ queryKey: ['announcements', slug] }),
  });
  return (
    <div className="reactions">
      {EMOJI.map((e) => {
        const count = a.reactions[e.key] ?? 0;
        const mine = a.myReactions.includes(e.key);
        if (!user && count === 0) return null;
        return (
          <button
            key={e.key}
            className="reaction"
            aria-pressed={mine}
            aria-label={`${e.label}${count ? `, ${count}` : ''}`}
            onClick={() => (user ? toggle.mutate({ emoji: e.key, on: !mine }) : navigate(`/login?next=/e/${slug}/updates`))}
          >
            <span aria-hidden="true">{e.char}</span>
            {count > 0 && <span>{count}</span>}
          </button>
        );
      })}
    </div>
  );
}

export function UpdatesPage() {
  const { slug = '' } = useParams();
  const event = useEvent(slug);
  const updates = useAnnouncements(slug);
  const tz = useTz(slug);
  const now = useNow(30_000);
  const [tab, setTab] = useState<'news' | 'chat'>('news');
  const chatOn = event.data?.chatEnabled ?? false;

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
        <p className="lede">
          {tab === 'news' ? 'Official news from the organisers. This page refreshes on its own.' : 'Chat with everyone at the event.'}
        </p>
      </div>
      {chatOn && (
        <div className="admin-tabs" role="tablist" aria-label="Updates">
          <button role="tab" className="tab-btn" aria-selected={tab === 'news'} onClick={() => setTab('news')}>
            Announcements
          </button>
          <button role="tab" className="tab-btn" aria-selected={tab === 'chat'} onClick={() => setTab('chat')}>
            Chat
          </button>
        </div>
      )}
      {tab === 'chat' && chatOn ? (
        <ChatPanel slug={slug} eventId={event.data!.id} tz={tz} />
      ) : updates.data.length === 0 ? (
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
              <Reactions slug={slug} a={a} />
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

function ChatPanel({ slug, eventId, tz }: { slug: string; eventId: string; tz: string }) {
  const { user, canManage } = useAuth();
  const chat = useChat(slug, !!user);
  const client = useQueryClient();
  const [text, setText] = useState('');
  const [error, setError] = useState<string | null>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const count = chat.data?.length ?? 0;

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end' });
  }, [count]);

  const send = useMutation({
    mutationFn: (body: string) => api<ChatMessageView>(`/api/v1/me/chat?event=${encodeURIComponent(slug)}`, { method: 'POST', body: { body } }),
    onSuccess: () => {
      setText('');
      setError(null);
      client.invalidateQueries({ queryKey: ['chat', slug] });
    },
    onError: (e) => setError(e instanceof Error ? e.message : 'Message not sent.'),
  });

  const remove = useMutation({
    mutationFn: (m: ChatMessageView) =>
      api<void>(m.mine ? `/api/v1/me/chat/${m.id}` : `/api/v1/admin/events/${eventId}/chat/${m.id}`, { method: 'DELETE' }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['chat', slug] }),
  });

  if (!user) {
    return (
      <Empty title="Log in to chat">
        <p>The chat is for people at the event.</p>
        <Link className="btn" to={`/login?next=/e/${slug}/updates`}>
          Log in
        </Link>
      </Empty>
    );
  }
  if (chat.isPending) return <Loading />;
  if (chat.isError) return <ErrorState error={chat.error} onRetry={() => chat.refetch()} />;
  const moderator = canManage(eventId);

  return (
    <div className="chat">
      {chat.data.length === 0 ? (
        <p className="muted">No messages yet. Say hello.</p>
      ) : (
        <ol className="chat-list">
          {chat.data.map((m) => (
            <li key={m.id} className={`chat-msg ${m.mine ? 'is-mine' : ''} ${m.fromOrganiser ? 'is-organiser' : ''}`}>
              <div className="chat-meta">
                <strong>{m.authorName}</strong>
                {m.fromOrganiser && <span className="org-tag">Organiser</span>}
                <time dateTime={m.createdAt}>{formatTime(m.createdAt, tz)}</time>
              </div>
              {m.removed ? <p className="muted">Message removed.</p> : <p className="post-body">{m.body}</p>}
              {!m.removed && (m.mine || moderator) && (
                <button
                  className="btn link small-link"
                  onClick={() => window.confirm('Remove this message?') && remove.mutate(m)}
                >
                  Remove
                </button>
              )}
            </li>
          ))}
        </ol>
      )}
      <div ref={endRef} />
      <form
        className="chat-form"
        onSubmit={(e) => {
          e.preventDefault();
          if (text.trim()) send.mutate(text.trim());
        }}
      >
        {error && <Notice kind="error">{error}</Notice>}
        <label className="visually-hidden" htmlFor="chat-input">
          Message
        </label>
        <textarea
          id="chat-input"
          rows={2}
          maxLength={1000}
          placeholder="Write a message"
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              if (text.trim()) send.mutate(text.trim());
            }
          }}
        />
        <button className="btn" disabled={send.isPending || !text.trim()}>
          Send
        </button>
      </form>
      <p className="muted">Be kind. Organisers can remove messages.</p>
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
