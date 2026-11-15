import { useId, type ReactNode } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../api/http';

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <p className="loading" role="status">
      {label}
    </p>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const notFound = error instanceof ApiError && error.status === 404;
  const message = error instanceof Error ? error.message : 'Something went wrong.';
  return (
    <div className="empty" role="alert">
      <h2>{notFound ? 'Not found' : "This didn't load"}</h2>
      <p>{notFound ? 'It may have been moved or removed.' : message}</p>
      {notFound ? (
        <Link className="btn secondary" to="/">
          Go to the agenda
        </Link>
      ) : (
        onRetry && (
          <button className="btn secondary" onClick={onRetry}>
            Try again
          </button>
        )
      )}
    </div>
  );
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <h2>{title}</h2>
      {children}
    </div>
  );
}

export function Notice({ kind = 'info', children }: { kind?: 'info' | 'error' | 'ok'; children: ReactNode }) {
  return (
    <div className={`notice ${kind === 'error' ? 'is-error' : kind === 'ok' ? 'is-ok' : ''}`} role={kind === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  );
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]!.toUpperCase())
    .join('');
}

export function Avatar({ name, url, size }: { name: string; url?: string | null; size?: 'medium' | 'large' }) {
  const cls = `avatar ${size ?? ''}`;
  if (url) return <img className={cls} src={url} alt="" loading="lazy" />;
  return (
    <span className={cls} aria-hidden="true">
      {initials(name)}
    </span>
  );
}

/** Only render links that are plain http(s); anything else is dropped. */
export function safeUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  return /^https?:\/\//i.test(url) ? url : null;
}

interface FieldProps {
  label: string;
  error?: string;
  hint?: string;
  children: (props: { id: string; 'aria-invalid': boolean; 'aria-describedby'?: string }) => ReactNode;
}

export function Field({ label, error, hint, children }: FieldProps) {
  const id = useId();
  const describedBy = [hint ? `${id}-hint` : '', error ? `${id}-err` : ''].filter(Boolean).join(' ') || undefined;
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children({ id, 'aria-invalid': !!error, 'aria-describedby': describedBy })}
      {hint && (
        <span className="hint" id={`${id}-hint`}>
          {hint}
        </span>
      )}
      {error && (
        <span className="error" id={`${id}-err`}>
          {error}
        </span>
      )}
    </div>
  );
}

export function BookmarkIcon({ filled }: { filled: boolean }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" fill={filled ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="2" strokeLinejoin="round">
      <path d="M6 3h12v18l-6-4.5L6 21z" />
    </svg>
  );
}

export function BookmarkButton({ saved, title, onToggle }: { saved: boolean; title: string; onToggle: () => void }) {
  return (
    <button
      type="button"
      className="bookmark"
      aria-pressed={saved}
      aria-label={saved ? `Remove "${title}" from my schedule` : `Save "${title}" to my schedule`}
      onClick={onToggle}
    >
      <BookmarkIcon filled={saved} />
    </button>
  );
}
