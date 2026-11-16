import { useEffect, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../api/http';
import type { MyFeedbackView, SessionView, TicketView } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ErrorState, Loading, Notice } from '../components/ui';
import { useNow } from '../lib/useNow';

// ---------------- Ticket (QR code for check-in) ----------------

export function TicketPage() {
  const { slug = '' } = useParams();
  const { user, loading } = useAuth();
  const ticket = useQuery({
    queryKey: ['me', 'ticket', slug],
    queryFn: () => api<TicketView>(`/api/v1/me/ticket?event=${encodeURIComponent(slug)}`),
    enabled: !!user,
    // Check now and then, so the page flips to "You're checked in" while they're at the door.
    refetchInterval: (q) => (q.state.data?.checkedInAt ? false : 10_000),
  });
  const [qr, setQr] = useState<string | null>(null);

  useEffect(() => {
    if (!ticket.data) return;
    let cancelled = false;
    import('qrcode')
      .then(({ default: QRCode }) => QRCode.toDataURL(ticket.data.qrText, { width: 320, margin: 2, errorCorrectionLevel: 'M', color: { dark: '#0f2a2c', light: '#ffffff' } }))
      .then((url) => !cancelled && setQr(url))
      .catch(() => setQr(null));
    return () => {
      cancelled = true;
    };
  }, [ticket.data]);

  if (loading) return <Loading />;
  if (!user) {
    return (
      <div className="narrow stack">
        <h1>Your ticket</h1>
        <p>Log in to get the QR code you'll show at the door.</p>
        <div>
          <Link className="btn" to={`/login?next=${encodeURIComponent(`/e/${slug}/ticket`)}`}>
            Log in
          </Link>
        </div>
      </div>
    );
  }
  if (ticket.isPending) return <Loading />;
  if (ticket.isError) return <ErrorState error={ticket.error} onRetry={() => ticket.refetch()} />;
  const t = ticket.data;
  const pretty = `${t.code.slice(0, 4)} ${t.code.slice(4)}`;

  return (
    <div className="narrow stack-l ticket-page">
      <div className="page-title">
        <h1>Your ticket</h1>
        <p className="lede">{t.eventName}</p>
      </div>
      {t.checkedInAt ? (
        <Notice kind="ok">You're checked in. Enjoy the day!</Notice>
      ) : (
        <p>Show this at the door. Turn your screen brightness up if the scanner struggles.</p>
      )}
      <figure className={`ticket ${t.checkedInAt ? 'is-used' : ''}`}>
        {qr ? <img src={qr} width={260} height={260} alt={`Check-in QR code. Code ${pretty}`} /> : <div className="ticket-placeholder" />}
        <figcaption>
          <span className="muted small">Code</span>
          <strong className="ticket-code">{pretty}</strong>
          <span className="muted small">{user.displayName}</span>
        </figcaption>
      </figure>
    </div>
  );
}

// ---------------- Session feedback ----------------

const RATING_WORDS = ['Poor', 'Fair', 'Good', 'Very good', 'Excellent'];

export function SessionFeedback({ slug, session }: { slug: string; session: SessionView }) {
  const { user } = useAuth();
  const now = useNow();
  const client = useQueryClient();
  const mine = useQuery({
    queryKey: ['me', 'feedback', slug],
    queryFn: () => api<MyFeedbackView[]>(`/api/v1/me/feedback?event=${encodeURIComponent(slug)}`),
    enabled: !!user,
  });
  const existing = mine.data?.find((f) => f.sessionId === session.id);
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (existing) {
      setRating(existing.rating);
      setComment(existing.comment ?? '');
    }
  }, [existing]);

  const save = useMutation({
    mutationFn: () =>
      api<MyFeedbackView>(`/api/v1/me/sessions/${session.id}/feedback`, { method: 'PUT', body: { rating, comment } }),
    onSuccess: () => {
      setEditing(false);
      setError(null);
      client.invalidateQueries({ queryKey: ['me', 'feedback', slug] });
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : 'Could not save your feedback.'),
  });
  const remove = useMutation({
    mutationFn: () => api<void>(`/api/v1/me/sessions/${session.id}/feedback`, { method: 'DELETE' }),
    onSuccess: () => {
      setRating(0);
      setComment('');
      client.invalidateQueries({ queryKey: ['me', 'feedback', slug] });
    },
  });

  if (session.status === 'CANCELLED' || session.type === 'BREAK') return null;
  if (new Date(session.startsAt).getTime() > now) return null;

  if (!user) {
    return (
      <section className="panel stack">
        <h2>How was it?</h2>
        <p className="muted">
          <Link to={`/login?next=${encodeURIComponent(`/e/${slug}/sessions/${session.id}`)}`}>Log in</Link> to rate this session.
        </p>
      </section>
    );
  }

  if (existing && !editing) {
    return (
      <section className="panel stack">
        <h2>Your feedback</h2>
        <p>
          <Stars value={existing.rating} /> <span className="muted">{RATING_WORDS[existing.rating - 1]}</span>
        </p>
        {existing.comment && <p className="prose">{existing.comment}</p>}
        <div className="row">
          <button className="btn secondary small" onClick={() => setEditing(true)}>
            Change
          </button>
          <button className="btn secondary small" onClick={() => remove.mutate()} disabled={remove.isPending}>
            Remove
          </button>
        </div>
      </section>
    );
  }

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (rating > 0) save.mutate();
  };

  return (
    <form className="panel form" onSubmit={submit}>
      <h2>How was it?</h2>
      <p className="muted small">The organisers see your rating and comment, but not your name.</p>
      {error && <Notice kind="error">{error}</Notice>}
      <fieldset className="star-input">
        <legend className="visually-hidden">Rating</legend>
        {[1, 2, 3, 4, 5].map((n) => (
          <label key={n} className={n <= rating ? 'is-on' : ''}>
            <input type="radio" name="rating" value={n} checked={rating === n} onChange={() => setRating(n)} />
            <span aria-hidden="true">★</span>
            <span className="visually-hidden">
              {n} star{n > 1 ? 's' : ''}, {RATING_WORDS[n - 1]}
            </span>
          </label>
        ))}
        {rating > 0 && <span className="muted">{RATING_WORDS[rating - 1]}</span>}
      </fieldset>
      <label className="field">
        <span className="label">Comment (optional)</span>
        <textarea rows={3} maxLength={1000} value={comment} onChange={(e) => setComment(e.target.value)} />
      </label>
      <div className="row">
        <button className="btn" disabled={rating === 0 || save.isPending}>
          {save.isPending ? 'Sending…' : 'Send feedback'}
        </button>
        {editing && (
          <button type="button" className="btn secondary" onClick={() => setEditing(false)}>
            Cancel
          </button>
        )}
      </div>
    </form>
  );
}

export function Stars({ value }: { value: number }) {
  return (
    <span className="stars" aria-label={`${value} out of 5`}>
      {'★'.repeat(Math.round(value))}
      <span className="stars-off">{'★'.repeat(5 - Math.round(value))}</span>
    </span>
  );
}
