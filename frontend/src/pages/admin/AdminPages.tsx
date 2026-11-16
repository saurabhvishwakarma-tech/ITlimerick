import { useState } from 'react';
import { Link, Navigate, NavLink, useNavigate, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/http';
import type {
  AnnouncementView,
  EventView,
  MemberRole,
  MemberView,
  RoomView,
  SessionView,
  SpeakerView,
  SponsorView,
} from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Empty, ErrorState, Loading, Notice } from '../../components/ui';
import { formatDateTime } from '../../lib/time';
import { CrudTab, EditDialog, EntityForm, type FieldDef, type FormValues } from './CrudTab';
import { HackathonTab } from './HackathonAdmin';
import { CheckinTab, FeedbackTab } from './CheckinAdmin';

const DEFAULT_TZ = 'Europe/Dublin';

const eventFields: FieldDef[] = [
  { name: 'name', label: 'Event name', type: 'text', required: true },
  { name: 'slug', label: 'Web address', type: 'text', required: true, hint: 'Lowercase letters, numbers and dashes, e.g. summit-2027. Appears in links as /e/summit-2027.' },
  { name: 'tagline', label: 'Tagline', type: 'text' },
  { name: 'description', label: 'Description', type: 'textarea' },
  { name: 'venueName', label: 'Venue', type: 'text' },
  { name: 'venueAddress', label: 'Venue address', type: 'text' },
  { name: 'startsAt', label: 'Starts', type: 'datetime', hint: 'Venue time.' },
  { name: 'endsAt', label: 'Ends', type: 'datetime', hint: 'Venue time.' },
  { name: 'timezone', label: 'Timezone', type: 'text', required: true, hint: 'Keep Europe/Dublin unless the event is elsewhere.' },
  {
    name: 'status',
    label: 'Visibility',
    type: 'select',
    required: true,
    options: [
      { value: 'DRAFT', label: 'Draft: only organisers can see it' },
      { value: 'PUBLISHED', label: 'Published: visible to everyone' },
    ],
  },
  { name: 'hackathonEnabled', label: 'This event has a hackathon', type: 'checkbox', hint: 'Shows Teams and Vote to attendees.' },
  { name: 'teamMaxSize', label: 'Maximum team size', type: 'number', hint: 'People can only join a team with a code while it has space.' },
  { name: 'votingOpensAt', label: 'Voting opens', type: 'datetime', hint: 'Venue time. Leave empty until you know.' },
  { name: 'votingClosesAt', label: 'Voting closes', type: 'datetime', hint: 'Venue time.' },
  { name: 'votingResultsPublic', label: 'Show vote results to everyone after voting closes', type: 'checkbox' },
  { name: 'chatEnabled', label: 'Turn on attendee chat', type: 'checkbox', hint: 'Logged-in attendees can post on the Updates page. Organisers can remove messages.' },
];

function eventForm(e?: EventView): FormValues {
  return {
    name: e?.name ?? '',
    slug: e?.slug ?? '',
    tagline: e?.tagline ?? '',
    description: e?.description ?? '',
    venueName: e?.venueName ?? '',
    venueAddress: e?.venueAddress ?? '',
    startsAt: e?.startsAt ?? null,
    endsAt: e?.endsAt ?? null,
    timezone: e?.timezone ?? DEFAULT_TZ,
    status: e?.status ?? 'DRAFT',
    hackathonEnabled: e?.hackathonEnabled ?? false,
    teamMaxSize: e?.teamMaxSize ?? 5,
    votingOpensAt: e?.votingOpensAt ?? null,
    votingClosesAt: e?.votingClosesAt ?? null,
    votingResultsPublic: e?.votingResultsPublic ?? false,
    chatEnabled: e?.chatEnabled ?? false,
  };
}

function RequireOrganiser({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  if (loading) return <Loading />;
  if (!user) return <Navigate to="/login?next=/admin" replace />;
  if (!user.platformAdmin && user.organiserOf.length === 0) {
    return (
      <Empty title="Organisers only">
        <p>Your account doesn't have organiser access. Ask an existing organiser to add you on the Team tab.</p>
      </Empty>
    );
  }
  return <>{children}</>;
}

export function AdminHome() {
  return (
    <RequireOrganiser>
      <AdminHomeInner />
    </RequireOrganiser>
  );
}

function AdminHomeInner() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const client = useQueryClient();
  const events = useQuery({ queryKey: ['admin', 'events'], queryFn: () => api<EventView[]>('/api/v1/admin/events') });
  const [creating, setCreating] = useState(false);

  if (events.isPending) return <Loading />;
  if (events.isError) return <ErrorState error={events.error} onRetry={() => events.refetch()} />;

  return (
    <div className="stack-l">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <div className="page-title">
          <h1>Admin</h1>
          <p className="lede">Choose an event to manage.</p>
        </div>
        {user?.platformAdmin && (
          <button className="btn" onClick={() => setCreating(true)}>
            Create event
          </button>
        )}
      </div>
      {events.data.length === 0 ? (
        <Empty title="No events yet">
          <p>{user?.platformAdmin ? 'Create the first event to start adding the agenda.' : 'You are not an organiser of any event yet.'}</p>
        </Empty>
      ) : (
        <ul className="feed">
          {events.data.map((e) => (
            <li key={e.id} className="post">
              <div className="row" style={{ justifyContent: 'space-between' }}>
                <Link to={`/admin/events/${e.id}/agenda`}>
                  <h3>{e.name}</h3>
                </Link>
                <span className={`status-pill ${e.status === 'DRAFT' ? 'is-draft' : ''}`}>{e.status === 'DRAFT' ? 'Draft' : 'Published'}</span>
              </div>
              <span className="muted">{e.startsAt ? formatDateTime(e.startsAt, e.timezone) : 'No date set'}</span>
            </li>
          ))}
        </ul>
      )}
      {creating && (
        <EditDialog
          title="Create event"
          fields={eventFields}
          initial={eventForm()}
          timezone={DEFAULT_TZ}
          saveLabel="Create event"
          onClose={() => setCreating(false)}
          onSave={async (values) => {
            const created = await api<EventView>('/api/v1/admin/events', { method: 'POST', body: values });
            client.invalidateQueries({ queryKey: ['admin', 'events'] });
            setCreating(false);
            navigate(`/admin/events/${created.id}/details`);
          }}
        />
      )}
    </div>
  );
}

const TABS = [
  ['agenda', 'Agenda'],
  ['speakers', 'Speakers'],
  ['partners', 'Partners'],
  ['updates', 'Updates'],
  ['hackathon', 'Hackathon'],
  ['checkin', 'Check-in'],
  ['feedback', 'Feedback'],
  ['rooms', 'Rooms'],
  ['team', 'Team'],
  ['details', 'Event details'],
] as const;

export function AdminEvent() {
  return (
    <RequireOrganiser>
      <AdminEventInner />
    </RequireOrganiser>
  );
}

function AdminEventInner() {
  const { eventId = '', tab = 'agenda' } = useParams();
  const event = useQuery({
    queryKey: ['admin', 'event', eventId],
    queryFn: () => api<EventView>(`/api/v1/admin/events/${eventId}`),
  });

  if (event.isPending) return <Loading />;
  if (event.isError) return <ErrorState error={event.error} onRetry={() => event.refetch()} />;
  const e = event.data;
  const base = `/api/v1/admin/events/${eventId}`;

  return (
    <div className="stack">
      <Link className="back" to="/admin">
        All events
      </Link>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h1>{e.name}</h1>
        <div className="row">
          <span className={`status-pill ${e.status === 'DRAFT' ? 'is-draft' : ''}`}>{e.status === 'DRAFT' ? 'Draft' : 'Published'}</span>
          {e.status === 'PUBLISHED' && (
            <Link to={`/e/${e.slug}`} target="_blank">
              View public page
            </Link>
          )}
        </div>
      </div>
      <nav className="admin-tabs" aria-label="Manage event">
        {TABS.map(([key, label]) => (
          <NavLink key={key} to={`/admin/events/${eventId}/${key}`}>
            {label}
          </NavLink>
        ))}
      </nav>
      {tab === 'agenda' && <SessionsTab base={base} event={e} />}
      {tab === 'speakers' && <SpeakersTab base={base} event={e} />}
      {tab === 'partners' && <PartnersTab base={base} event={e} />}
      {tab === 'updates' && <UpdatesTab base={base} event={e} />}
      {tab === 'hackathon' && <HackathonTab base={base} event={e} />}
      {tab === 'checkin' && <CheckinTab base={base} event={e} />}
      {tab === 'feedback' && <FeedbackTab base={base} event={e} />}
      {tab === 'rooms' && <RoomsTab base={base} event={e} />}
      {tab === 'team' && <TeamTab base={base} />}
      {tab === 'details' && <DetailsTab event={e} />}
    </div>
  );
}

// ---------- Tabs ----------

function SessionsTab({ base, event }: { base: string; event: EventView }) {
  const rooms = useQuery({ queryKey: ['admin', `${base}/rooms`], queryFn: () => api<RoomView[]>(`${base}/rooms`) });
  const speakers = useQuery({ queryKey: ['admin', `${base}/speakers`], queryFn: () => api<SpeakerView[]>(`${base}/speakers`) });
  if (rooms.isPending || speakers.isPending) return <Loading />;
  const tz = event.timezone;

  const fields: FieldDef[] = [
    { name: 'title', label: 'Title', type: 'text', required: true },
    {
      name: 'type',
      label: 'Type',
      type: 'select',
      required: true,
      options: ['TALK', 'KEYNOTE', 'PANEL', 'WORKSHOP', 'BREAK', 'NETWORKING', 'OTHER'].map((t) => ({
        value: t,
        label: t.charAt(0) + t.slice(1).toLowerCase(),
      })),
    },
    { name: 'startsAt', label: 'Starts', type: 'datetime', required: true, hint: `Venue time (${tz}).` },
    { name: 'endsAt', label: 'Ends', type: 'datetime', hint: 'Leave empty if it simply runs until the next item.' },
    {
      name: 'roomId',
      label: 'Room',
      type: 'select',
      options: (rooms.data ?? []).map((r) => ({ value: r.id, label: r.name })),
      hint: rooms.data?.length ? undefined : 'Only needed if sessions run in parallel. Add rooms on the Rooms tab.',
    },
    {
      name: 'speakerIds',
      label: 'Speakers',
      type: 'multiselect',
      options: (speakers.data ?? []).map((s) => ({ value: s.id, label: s.name + (s.published ? '' : ' (hidden)') })),
      hint: 'Add speakers on the Speakers tab first.',
    },
    { name: 'description', label: 'Description', type: 'textarea' },
    {
      name: 'status',
      label: 'Status',
      type: 'select',
      required: true,
      options: [
        { value: 'SCHEDULED', label: 'Scheduled' },
        { value: 'CANCELLED', label: 'Cancelled (stays visible, marked as cancelled)' },
      ],
    },
  ];

  return (
    <CrudTab<SessionView>
      endpoint={`${base}/sessions`}
      noun="session"
      timezone={tz}
      fields={fields}
      emptyText="Add the first talk, break or panel to build the agenda."
      invalidate={[['agenda', event.slug]]}
      columns={[
        { label: 'Time', render: (s) => formatDateTime(s.startsAt, tz) },
        { label: 'Title', render: (s) => <strong>{s.title}</strong> },
        { label: 'Speakers', render: (s) => s.speakers.map((x) => x.name).join(', ') || '' },
        { label: 'Room', render: (s) => s.room?.name ?? '' },
        { label: 'Status', render: (s) => (s.status === 'CANCELLED' ? <span className="cancelled">Cancelled</span> : '') },
      ]}
      toForm={(s) => ({
        title: s?.title ?? '',
        type: s?.type ?? 'TALK',
        startsAt: s?.startsAt ?? event.startsAt ?? null,
        endsAt: s?.endsAt ?? null,
        roomId: s?.room?.id ?? '',
        speakerIds: s?.speakers.map((x) => x.id) ?? [],
        description: s?.description ?? '',
        status: s?.status ?? 'SCHEDULED',
      })}
    />
  );
}

function SpeakersTab({ base, event }: { base: string; event: EventView }) {
  return (
    <CrudTab<SpeakerView>
      endpoint={`${base}/speakers`}
      noun="speaker"
      timezone={event.timezone}
      emptyText="Add speakers here, then attach them to sessions on the Agenda tab."
      invalidate={[['speakers', event.slug], ['agenda', event.slug]]}
      fields={[
        { name: 'name', label: 'Name', type: 'text', required: true },
        { name: 'headline', label: 'Job title', type: 'text' },
        { name: 'company', label: 'Organisation', type: 'text' },
        { name: 'bio', label: 'Bio', type: 'textarea' },
        { name: 'photoUrl', label: 'Photo link', type: 'url', hint: 'A full https:// link to a square photo. Only use photos the speaker has agreed to share.' },
        { name: 'linkedinUrl', label: 'LinkedIn link', type: 'url' },
        { name: 'websiteUrl', label: 'Website link', type: 'url' },
        { name: 'sortOrder', label: 'Order', type: 'number', hint: 'Lower numbers are listed first.' },
        { name: 'published', label: 'Show on the public site', type: 'checkbox', hint: 'Untick until the speaker has approved their profile.' },
      ]}
      columns={[
        { label: 'Name', render: (s) => <strong>{s.name}</strong> },
        { label: 'Organisation', render: (s) => s.company ?? '' },
        { label: 'Public', render: (s) => (s.published ? 'Yes' : <span className="status-pill is-draft">Hidden</span>) },
      ]}
      toForm={(s) => ({
        name: s?.name ?? '',
        headline: s?.headline ?? '',
        company: s?.company ?? '',
        bio: s?.bio ?? '',
        photoUrl: s?.photoUrl ?? '',
        linkedinUrl: s?.linkedinUrl ?? '',
        websiteUrl: s?.websiteUrl ?? '',
        sortOrder: s?.sortOrder ?? 0,
        published: s?.published ?? false,
      })}
    />
  );
}

function PartnersTab({ base, event }: { base: string; event: EventView }) {
  return (
    <CrudTab<SponsorView>
      endpoint={`${base}/sponsors`}
      noun="partner"
      timezone={event.timezone}
      emptyText="Add sponsors and partners, grouped by tier."
      invalidate={[['sponsors', event.slug]]}
      intro={<p className="muted">Tiers are shown in order of tier rank (1 first). Partners in the same tier share a rank.</p>}
      fields={[
        { name: 'name', label: 'Name', type: 'text', required: true },
        { name: 'tier', label: 'Tier', type: 'text', required: true, hint: 'e.g. Lead Partner, Gold, Community Partner.' },
        { name: 'tierRank', label: 'Tier rank', type: 'number', hint: '1 for the top tier, 2 for the next, and so on.' },
        { name: 'description', label: 'Description', type: 'textarea' },
        { name: 'logoUrl', label: 'Logo link', type: 'url', hint: 'A full https:// link to the logo image.' },
        { name: 'websiteUrl', label: 'Website link', type: 'url' },
        { name: 'sortOrder', label: 'Order within tier', type: 'number' },
      ]}
      columns={[
        { label: 'Name', render: (s) => <strong>{s.name}</strong> },
        { label: 'Tier', render: (s) => `${s.tier} (${s.tierRank})` },
      ]}
      toForm={(s) => ({
        name: s?.name ?? '',
        tier: s?.tier ?? 'Partner',
        tierRank: s?.tierRank ?? 1,
        description: s?.description ?? '',
        logoUrl: s?.logoUrl ?? '',
        websiteUrl: s?.websiteUrl ?? '',
        sortOrder: s?.sortOrder ?? 0,
      })}
    />
  );
}

function UpdatesTab({ base, event }: { base: string; event: EventView }) {
  return (
    <CrudTab<AnnouncementView>
      endpoint={`${base}/announcements`}
      noun="update"
      timezone={event.timezone}
      emptyText="Post room changes, reminders and news. Phones pick them up within about 30 seconds."
      invalidate={[['announcements', event.slug]]}
      fields={[
        { name: 'title', label: 'Headline', type: 'text', required: true },
        { name: 'body', label: 'Details', type: 'textarea' },
        {
          name: 'priority',
          label: 'Priority',
          type: 'select',
          required: true,
          options: [
            { value: 'NORMAL', label: 'Normal' },
            { value: 'URGENT', label: 'Urgent: shown as a banner on the agenda for 3 hours' },
          ],
        },
        { name: 'pinned', label: 'Pin to the top of the updates list', type: 'checkbox' },
        { name: 'publishedAt', label: 'Publish at', type: 'datetime', hint: 'Leave as is to publish now, or pick a later time to schedule it.' },
      ]}
      columns={[
        { label: 'Published', render: (a) => formatDateTime(a.publishedAt, event.timezone) },
        { label: 'Headline', render: (a) => <strong>{a.title}</strong> },
        { label: 'Priority', render: (a) => (a.priority === 'URGENT' ? <span className="status-pill is-draft">Urgent</span> : 'Normal') },
      ]}
      toForm={(a) => ({
        title: a?.title ?? '',
        body: a?.body ?? '',
        priority: a?.priority ?? 'NORMAL',
        pinned: a?.pinned ?? false,
        publishedAt: a?.publishedAt ?? new Date().toISOString(),
      })}
    />
  );
}

function RoomsTab({ base, event }: { base: string; event: EventView }) {
  return (
    <CrudTab<RoomView>
      endpoint={`${base}/rooms`}
      noun="room"
      timezone={event.timezone}
      emptyText="Single-track event? You don't need rooms. Add them only if sessions run at the same time."
      invalidate={[['agenda', event.slug]]}
      fields={[
        { name: 'name', label: 'Room name', type: 'text', required: true },
        { name: 'sortOrder', label: 'Order', type: 'number' },
      ]}
      columns={[{ label: 'Room', render: (r) => <strong>{r.name}</strong> }]}
      toForm={(r) => ({ name: r?.name ?? '', sortOrder: r?.sortOrder ?? 0 })}
    />
  );
}

const ROLE_LABEL: Record<MemberRole, string> = {
  ORGANISER: 'Organiser (can edit everything for this event)',
  SPEAKER: 'Speaker',
  SPONSOR: 'Sponsor',
  ATTENDEE: 'Attendee',
};

function TeamTab({ base }: { base: string }) {
  const client = useQueryClient();
  const members = useQuery({ queryKey: ['admin', `${base}/members`], queryFn: () => api<MemberView[]>(`${base}/members`) });
  const [message, setMessage] = useState<string | null>(null);
  const remove = useMutation({
    mutationFn: (id: string) => api<void>(`${base}/members/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ['admin', `${base}/members`] });
      setMessage('Removed.');
    },
  });

  if (members.isPending) return <Loading />;
  if (members.isError) return <ErrorState error={members.error} onRetry={() => members.refetch()} />;

  return (
    <div className="stack-l">
      <section className="panel">
        <h2>Add someone</h2>
        <p className="muted">They need to create an account first. Then add them here with their email.</p>
        <EntityForm
          fields={[
            { name: 'email', label: 'Their email', type: 'text', required: true },
            {
              name: 'role',
              label: 'Role',
              type: 'select',
              required: true,
              options: (Object.keys(ROLE_LABEL) as MemberRole[]).map((r) => ({ value: r, label: ROLE_LABEL[r] })),
            },
          ]}
          initial={{ email: '', role: 'ORGANISER' }}
          timezone={DEFAULT_TZ}
          saveLabel="Add to team"
          onSave={async (values) => {
            await api(`${base}/members`, { method: 'POST', body: values });
            client.invalidateQueries({ queryKey: ['admin', `${base}/members`] });
            setMessage(`Added ${values.email}.`);
          }}
        />
      </section>
      {message && <Notice>{message}</Notice>}
      {members.data.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col">Email</th>
                <th scope="col">Role</th>
                <th scope="col">
                  <span className="visually-hidden">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {members.data.map((m) => (
                <tr key={m.id}>
                  <td>{m.displayName}</td>
                  <td>{m.email}</td>
                  <td>{m.role.charAt(0) + m.role.slice(1).toLowerCase()}</td>
                  <td>
                    <button
                      className="btn small danger"
                      onClick={() => window.confirm(`Remove ${m.displayName}'s ${m.role.toLowerCase()} role?`) && remove.mutate(m.id)}
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function DetailsTab({ event }: { event: EventView }) {
  const client = useQueryClient();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [message, setMessage] = useState<string | null>(null);

  return (
    <div className="stack-l narrow">
      {message && <Notice kind="ok">{message}</Notice>}
      <section className="panel">
        <EntityForm
          key={event.id + event.status}
          fields={eventFields}
          initial={eventForm(event)}
          timezone={event.timezone}
          saveLabel="Save details"
          onSave={async (values) => {
            await api(`/api/v1/admin/events/${event.id}`, { method: 'PUT', body: values });
            client.invalidateQueries({ queryKey: ['admin'] });
            client.invalidateQueries({ queryKey: ['event'] });
            client.invalidateQueries({ queryKey: ['events'] });
            setMessage('Event details saved.');
          }}
        />
      </section>
      {user?.platformAdmin && (
        <section className="panel">
          <h2>Delete event</h2>
          <p className="muted">Deletes the event with its whole agenda, speakers, partners and updates. This can't be undone.</p>
          <div>
            <button
              className="btn danger"
              onClick={async () => {
                if (!window.confirm(`Delete "${event.name}" and everything in it? This can't be undone.`)) return;
                await api(`/api/v1/admin/events/${event.id}`, { method: 'DELETE' });
                client.invalidateQueries({ queryKey: ['admin'] });
                navigate('/admin');
              }}
            >
              Delete this event
            </button>
          </div>
        </section>
      )}
    </div>
  );
}
