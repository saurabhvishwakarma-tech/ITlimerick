import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { api, ApiError } from '../api/http';
import { useAuth } from '../auth/AuthContext';
import { lastEventSlug } from '../components/Shell';
import { Field, Notice } from '../components/ui';

function useAfterLogin() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  return () => {
    const next = params.get('next');
    // Only allow internal paths, never full URLs, to avoid open redirects.
    const safeNext = next && next.startsWith('/') && !next.startsWith('//') ? next : null;
    const slug = lastEventSlug();
    navigate(safeNext ?? (slug ? `/e/${slug}` : '/'), { replace: true });
  };
}

function errorOf(err: unknown): ApiError | null {
  return err instanceof ApiError ? err : null;
}

export function LoginPage() {
  const { login } = useAuth();
  const done = useAfterLogin();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password);
      done();
    } catch (err) {
      setError(errorOf(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-card stack-l">
      <div className="page-title">
        <h1>Log in</h1>
        <p className="lede">Keep your saved sessions on every device.</p>
      </div>
      <form className="panel form" onSubmit={submit} noValidate>
        {error && <Notice kind="error">{error.message}</Notice>}
        <Field label="Email" error={error?.fields.email}>
          {(p) => <input {...p} type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />}
        </Field>
        <Field label="Password" error={error?.fields.password}>
          {(p) => (
            <input {...p} type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
          )}
        </Field>
        <button className="btn" disabled={busy}>
          {busy ? 'Logging in…' : 'Log in'}
        </button>
        <p className="row" style={{ justifyContent: 'space-between' }}>
          <Link to="/forgot-password">Forgot your password?</Link>
          <Link to="/register">Create an account</Link>
        </p>
      </form>
    </div>
  );
}

export function RegisterPage() {
  const { register } = useAuth();
  const done = useAfterLogin();
  const [form, setForm] = useState({ displayName: '', email: '', password: '', acceptPrivacy: false, marketingOptIn: false });
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof form, v: string | boolean) => setForm((f) => ({ ...f, [k]: v }));

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await register(form);
      done();
    } catch (err) {
      setError(errorOf(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-card stack-l">
      <div className="page-title">
        <h1>Create an account</h1>
        <p className="lede">You only need an account to sync your schedule. Everything else works without one.</p>
      </div>
      <form className="panel form" onSubmit={submit} noValidate>
        {error && <Notice kind="error">{error.message}</Notice>}
        <Field label="Your name" error={error?.fields.displayName} hint="Shown only to the organisers.">
          {(p) => <input {...p} type="text" autoComplete="name" required maxLength={80} value={form.displayName} onChange={(e) => set('displayName', e.target.value)} />}
        </Field>
        <Field label="Email" error={error?.fields.email}>
          {(p) => <input {...p} type="email" autoComplete="email" required value={form.email} onChange={(e) => set('email', e.target.value)} />}
        </Field>
        <Field label="Password" error={error?.fields.password} hint="At least 10 characters.">
          {(p) => (
            <input {...p} type="password" autoComplete="new-password" required minLength={10} value={form.password} onChange={(e) => set('password', e.target.value)} />
          )}
        </Field>
        <label className="check">
          <input type="checkbox" checked={form.acceptPrivacy} onChange={(e) => set('acceptPrivacy', e.target.checked)} />
          <span>
            I've read the{' '}
            <Link to="/privacy" target="_blank">
              privacy notice
            </Link>
            .
          </span>
        </label>
        {error?.fields.acceptPrivacy && <span className="error">{error.fields.acceptPrivacy}</span>}
        <label className="check">
          <input type="checkbox" checked={form.marketingOptIn} onChange={(e) => set('marketingOptIn', e.target.checked)} />
          <span>Email me about future IT Limerick events. Optional, and you can change this any time.</span>
        </label>
        <button className="btn" disabled={busy}>
          {busy ? 'Creating account…' : 'Create account'}
        </button>
        <p>
          Already have one? <Link to="/login">Log in</Link>
        </p>
      </form>
    </div>
  );
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api('/api/v1/auth/password/forgot', { method: 'POST', body: { email } });
      setSent(true);
    } catch (err) {
      setError(errorOf(err));
    }
  }

  return (
    <div className="auth-card stack-l">
      <div className="page-title">
        <h1>Reset your password</h1>
      </div>
      {sent ? (
        <Notice kind="ok">If an account uses {email}, we've sent it a reset link. It works for one hour.</Notice>
      ) : (
        <form className="panel form" onSubmit={submit} noValidate>
          {error && <Notice kind="error">{error.message}</Notice>}
          <Field label="Email" error={error?.fields.email}>
            {(p) => <input {...p} type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />}
          </Field>
          <button className="btn">Send reset link</button>
        </form>
      )}
    </div>
  );
}

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get('token') ?? '';
  const [password, setPassword] = useState('');
  const [error, setError] = useState<ApiError | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api('/api/v1/auth/password/reset', { method: 'POST', body: { token, newPassword: password } });
      navigate('/login?reset=1', { replace: true });
    } catch (err) {
      setError(errorOf(err));
    }
  }

  return (
    <div className="auth-card stack-l">
      <div className="page-title">
        <h1>Choose a new password</h1>
      </div>
      <form className="panel form" onSubmit={submit} noValidate>
        {!token && <Notice kind="error">This link is incomplete. Request a new one from the login page.</Notice>}
        {error && <Notice kind="error">{error.message}</Notice>}
        <Field label="New password" error={error?.fields.newPassword} hint="At least 10 characters.">
          {(p) => <input {...p} type="password" autoComplete="new-password" required minLength={10} value={password} onChange={(e) => setPassword(e.target.value)} />}
        </Field>
        <button className="btn" disabled={!token}>
          Save new password
        </button>
      </form>
    </div>
  );
}
