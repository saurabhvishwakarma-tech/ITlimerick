import { useEffect, type ReactNode } from 'react';
import { Link, NavLink, Outlet, useParams } from 'react-router';
import { useAnnouncements, useEvent } from '../api/queries';
import { useAuth } from '../auth/AuthContext';
import { BookmarkIcon } from './ui';

const LAST_EVENT_KEY = 'summit-last-event';

export function rememberEvent(slug: string) {
  try {
    localStorage.setItem(LAST_EVENT_KEY, slug);
  } catch {
    // ignore
  }
}

export function lastEventSlug(): string | null {
  try {
    return localStorage.getItem(LAST_EVENT_KEY);
  } catch {
    return null;
  }
}

function BrandMark() {
  return (
    <svg className="brand-mark" viewBox="0 0 64 64" aria-hidden="true">
      <rect width="64" height="64" rx="14" fill="#0a4248" />
      <path d="M10 40c8-6 14-6 22 0s14 6 22 0" fill="none" stroke="#f2b33d" strokeWidth="5" strokeLinecap="round" />
      <path d="M10 28c8-6 14-6 22 0s14 6 22 0" fill="none" stroke="#edeee9" strokeWidth="5" strokeLinecap="round" />
    </svg>
  );
}

const icons = {
  agenda: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </svg>
  ),
  speakers: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21c1-4 4-6 8-6s7 2 8 6" />
    </svg>
  ),
  partners: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" aria-hidden="true">
      <path d="M3 9l9-6 9 6v11H3z" />
      <path d="M9 20v-6h6v6" />
    </svg>
  ),
  updates: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M4 10v4h3l6 4V6L7 10z" />
      <path d="M17 9a4 4 0 010 6" />
    </svg>
  ),
};

function EventNav({ slug, variant, hasNewUpdates }: { slug: string; variant: 'top' | 'tabs'; hasNewUpdates: boolean }) {
  const items = [
    { to: `/e/${slug}`, label: 'Agenda', icon: icons.agenda, end: true },
    { to: `/e/${slug}/speakers`, label: 'Speakers', icon: icons.speakers },
    { to: `/e/${slug}/partners`, label: 'Partners', icon: icons.partners },
    { to: `/e/${slug}/updates`, label: 'Updates', icon: icons.updates, dot: hasNewUpdates },
    { to: `/e/${slug}/my-schedule`, label: 'My schedule', icon: <BookmarkIcon filled={false} /> },
  ];
  return (
    <nav className={variant === 'top' ? 'topnav' : 'tabbar'} aria-label={variant === 'top' ? 'Event' : 'Event sections'}>
      {items.map((item) => (
        <NavLink key={item.to} to={item.to} end={item.end}>
          {variant === 'tabs' && <span className={item.dot ? 'badge-dot' : undefined}>{item.icon}</span>}
          <span>{item.label}</span>
        </NavLink>
      ))}
    </nav>
  );
}

function AccountLink() {
  const { user, loading } = useAuth();
  if (loading) return <span className="account-link" aria-hidden="true">&nbsp;</span>;
  return user ? (
    <Link className="account-link" to="/account">
      {user.displayName.split(' ')[0]}
    </Link>
  ) : (
    <Link className="account-link" to="/login">
      Log in
    </Link>
  );
}

const SEEN_KEY = (slug: string) => `summit-updates-seen:${slug}`;

export function markUpdatesSeen(slug: string, latest: string) {
  try {
    localStorage.setItem(SEEN_KEY(slug), latest);
  } catch {
    // ignore
  }
}

/** Layout for pages that belong to one event: header, navigation, and the urgent-update banner. */
export function EventShell() {
  const { slug = '' } = useParams();
  const event = useEvent(slug);
  const updates = useAnnouncements(slug);

  useEffect(() => {
    if (event.data) {
      rememberEvent(slug);
      document.title = event.data.name;
    }
  }, [event.data, slug]);

  const latest = updates.data?.reduce<string | null>((max, a) => (!max || a.publishedAt > max ? a.publishedAt : max), null);
  let seen: string | null = null;
  try {
    seen = localStorage.getItem(SEEN_KEY(slug));
  } catch {
    seen = null;
  }
  const hasNew = !!latest && (!seen || latest > seen);

  return (
    <Chrome slug={slug} title={event.data?.name ?? 'IT Limerick Summit'} hasNewUpdates={hasNew}>
      <Outlet />
    </Chrome>
  );
}

/** Layout for account, login and admin pages. Keeps the last event's navigation for easy return. */
export function PlainShell() {
  const slug = lastEventSlug();
  return (
    <Chrome slug={slug} title="IT Limerick Summit" hasNewUpdates={false}>
      <Outlet />
    </Chrome>
  );
}

function Chrome({ slug, title, hasNewUpdates, children }: { slug: string | null; title: string; hasNewUpdates: boolean; children: ReactNode }) {
  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="topbar">
        <div className="topbar-inner">
          <Link className="brand" to={slug ? `/e/${slug}` : '/'}>
            <BrandMark />
            <span>{title}</span>
          </Link>
          {slug && <EventNav slug={slug} variant="top" hasNewUpdates={hasNewUpdates} />}
          <AccountLink />
        </div>
      </header>
      <main id="main" className="page">
        {children}
      </main>
      {slug && <EventNav slug={slug} variant="tabs" hasNewUpdates={hasNewUpdates} />}
    </>
  );
}
