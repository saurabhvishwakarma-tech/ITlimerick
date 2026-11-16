import { Link, Navigate } from 'react-router';
import { usePublishedEvents } from '../api/queries';
import { Empty, ErrorState, Loading } from '../components/ui';
import { formatDay } from '../lib/time';

/** "/" sends people straight to the current event; if several are published, lets them choose. */
export function HomeRedirect() {
  const events = usePublishedEvents();
  if (events.isPending) return <Loading />;
  if (events.isError && !events.data) return <ErrorState error={events.error} onRetry={() => events.refetch()} />;
  if (events.data.length === 1) return <Navigate to={`/e/${events.data[0].slug}`} replace />;
  if (events.data.length === 0) {
    return (
      <Empty title="Nothing published yet">
        <p>The summit programme will appear here once the organisers publish it.</p>
      </Empty>
    );
  }
  return (
    <div className="narrow stack">
      <h1>Events</h1>
      <ul className="feed">
        {events.data.map((e) => (
          <li key={e.id} className="post">
            <Link to={`/e/${e.slug}`}>
              <h3>{e.name}</h3>
            </Link>
            {e.startsAt && <span className="muted">{formatDay(e.startsAt, e.timezone)}</span>}
          </li>
        ))}
      </ul>
    </div>
  );
}

export function NotFoundPage() {
  return (
    <Empty title="Page not found">
      <p>The link may be out of date.</p>
      <Link className="btn secondary" to="/">
        Go to the agenda
      </Link>
    </Empty>
  );
}

/**
 * Privacy notice TEMPLATE. Everything in [square brackets] must be filled in and approved by the
 * organisers (the data controller) before launch. See docs/HANDOVER.md, "GDPR".
 */
export function PrivacyPage() {
  return (
    <article className="narrow stack">
      <div className="page-title">
        <h1>Privacy notice</h1>
        <p className="muted">Version 2026-11. Last updated [date].</p>
      </div>

      <h2>Who we are</h2>
      <p>
        This app is run by [legal name of the organising body] ("we"), the data controller for the personal data described
        here. Contact us about privacy at [privacy contact email].
      </p>

      <h2>What we collect, and why</h2>
      <p>
        You can browse the agenda, speakers, partners and updates without an account, and we collect no personal data when
        you do. Bookmarks you save without an account stay on your own device.
      </p>
      <p>If you create an account, we store:</p>
      <ul>
        <li>your email address, name and a securely hashed password, to run your account;</li>
        <li>
          if you log in with Google or LinkedIn: the id that service gives your account, plus the name and email address it
          shares with us. We don't receive your password, contacts or posts, and we don't keep their access tokens;
        </li>
        <li>the sessions you save, so your schedule syncs across devices;</li>
        <li>your ticket code and the time you were checked in at the door;</li>
        <li>
          ratings and comments you give sessions. Organisers see them without your name and may share them with speakers;
        </li>
        <li>
          if you take part in the hackathon: your team, the skills you list, and your vote. Your name is shown to the other
          people at the event as a team member;
        </li>
        <li>messages you post in the event chat and reactions to updates, which other logged-in attendees can see;</li>
        <li>a record of when you accepted this notice and whether you chose to receive emails about future events.</li>
      </ul>
      <p>
        We use this data to provide the service you asked for (our lawful basis is the performance of that service). Emails
        about future events are sent only if you opt in, and you can opt out at any time from your account page.
      </p>
      <p>We do not sell your data, share it with sponsors, use advertising trackers, or set non-essential cookies.</p>

      <h2>Cookies</h2>
      <p>
        We use strictly necessary cookies only: one keeps you logged in, one protects forms against cross-site attacks,
        and a short-lived one (ten minutes) makes Google or LinkedIn log-in safe. The app also keeps a copy of the public agenda in your browser so it works with poor Wi-Fi.
      </p>

      <h2>Who processes data for us</h2>
      <p>[Hosting provider and region, e.g. "Our hosting partner [name] runs the app on servers in the EU."]</p>
      <p>[Email provider, if password-reset emails are enabled.]</p>
      <p>If you choose to log in with Google or LinkedIn, that service confirms who you are under its own privacy policy.</p>

      <h2>How long we keep it</h2>
      <p>
        We delete accounts [number] months after the event unless you log in again before then. You can delete your account
        yourself at any time.
      </p>

      <h2>Your rights</h2>
      <p>
        You can access, correct, download and delete your data from your account page, or by contacting us. You can also
        object to or restrict how we use it. If you're unhappy with how we handle your data, you can complain to the Data
        Protection Commission at dataprotection.ie.
      </p>
    </article>
  );
}
