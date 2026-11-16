import { useEffect, useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../api/http';
import type { ProfileView, Provider } from '../api/types';
import { PROVIDER_NAMES, useProviders } from '../components/SocialLogin';
import { useAuth } from '../auth/AuthContext';
import { ErrorState, Field, Loading, Notice } from '../components/ui';

export function AccountPage() {
  const { user, loading, logout, refresh } = useAuth();
  const navigate = useNavigate();
  const client = useQueryClient();
  const profile = useQuery({ queryKey: ['me'], queryFn: () => api<ProfileView>('/api/v1/me'), enabled: !!user });
  const [name, setName] = useState('');
  const [saved, setSaved] = useState<string | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [pw, setPw] = useState({ currentPassword: '', newPassword: '' });
  const providers = useProviders();
  const [params] = useSearchParams();
  const justConnected = params.get('connected') as Provider | null;

  useEffect(() => {
    if (profile.data) setName(profile.data.displayName);
  }, [profile.data]);

  const update = useMutation({
    mutationFn: (body: object) => api<ProfileView>('/api/v1/me', { method: 'PATCH', body }),
    onSuccess: (p) => {
      client.setQueryData(['me'], p);
      refresh();
      setSaved('Name saved.');
    },
    onError: (e) => setError(e instanceof ApiError ? e : null),
  });

  const consent = useMutation({
    mutationFn: (marketing: boolean) => api<ProfileView>('/api/v1/me/consents', { method: 'PUT', body: { marketing } }),
    onSuccess: (p) => {
      client.setQueryData(['me'], p);
      setSaved(p.marketingConsent ? "You'll get emails about future events." : "You won't get emails about future events.");
    },
  });

  const changePw = useMutation({
    mutationFn: () => api<void>('/api/v1/me/password', { method: 'PUT', body: pw }),
    onSuccess: () => {
      setPw({ currentPassword: '', newPassword: '' });
      client.invalidateQueries({ queryKey: ['me'] });
      setSaved('Password saved. Other devices have been logged out.');
    },
    onError: (e) => setError(e instanceof ApiError ? e : null),
  });

  if (loading) return <Loading />;
  if (!user) return <Navigate to="/login?next=/account" replace />;
  if (profile.isPending) return <Loading />;
  if (profile.isError) return <ErrorState error={profile.error} onRetry={() => profile.refetch()} />;
  const p = profile.data;

  async function deleteAccount() {
    const ok = window.confirm(
      'Delete your account permanently? Your saved sessions and personal details will be erased. This cannot be undone.',
    );
    if (!ok) return;
    try {
      await api<void>('/api/v1/me', { method: 'DELETE' });
      client.clear();
      await refresh();
      navigate('/', { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e : null);
    }
  }

  async function disconnect(provider: Provider) {
    setSaved(null);
    setError(null);
    try {
      await api<void>(`/api/v1/me/connected-accounts/${provider}`, { method: 'DELETE' });
      await client.invalidateQueries({ queryKey: ['me'] });
      setSaved(`${PROVIDER_NAMES[provider]} disconnected.`);
    } catch (e) {
      setError(e instanceof ApiError ? e : null);
    }
  }

  async function onLogout() {
    await logout();
    navigate('/', { replace: true });
  }

  const submitName = (e: FormEvent) => {
    e.preventDefault();
    setSaved(null);
    setError(null);
    update.mutate({ displayName: name });
  };

  const submitPw = (e: FormEvent) => {
    e.preventDefault();
    setSaved(null);
    setError(null);
    changePw.mutate();
  };

  return (
    <div className="narrow stack-l">
      <div className="page-title">
        <h1>Your account</h1>
        <p className="lede">{p.email}</p>
      </div>

      {justConnected && PROVIDER_NAMES[justConnected] && !saved && (
        <Notice kind="ok">{PROVIDER_NAMES[justConnected]} is now connected. You can use it to log in.</Notice>
      )}
      {saved && <Notice kind="ok">{saved}</Notice>}
      {error && <Notice kind="error">{error.message}</Notice>}

      {(user.platformAdmin || user.organiserOf.length > 0) && (
        <section className="panel">
          <h2>Organiser tools</h2>
          <p className="muted">Manage the agenda, speakers, partners and live updates.</p>
          <Link className="btn" to="/admin">
            Open the admin panel
          </Link>
        </section>
      )}

      <form className="panel form" onSubmit={submitName}>
        <h2>Your name</h2>
        <Field label="Name" error={error?.fields.displayName}>
          {(fp) => <input {...fp} type="text" value={name} maxLength={80} onChange={(e) => setName(e.target.value)} />}
        </Field>
        <div>
          <button className="btn" disabled={update.isPending || name.trim() === p.displayName}>
            Save name
          </button>
        </div>
      </form>

      <section className="panel">
        <h2>Emails</h2>
        <label className="check">
          <input
            type="checkbox"
            checked={p.marketingConsent}
            disabled={consent.isPending}
            onChange={(e) => consent.mutate(e.target.checked)}
          />
          <span>Email me about future IT Limerick events.</span>
        </label>
      </section>

      {(providers.length > 0 || p.connectedAccounts.length > 0) && (
        <section className="panel stack">
          <h2>Ways to log in</h2>
          <ul className="login-methods">
            <li>
              <span>Email and password</span>
              <span className="muted">{p.hasPassword ? 'Set' : 'Not set'}</span>
            </li>
            {(['google', 'linkedin'] as Provider[])
              .filter((pr) => providers.includes(pr) || p.connectedAccounts.includes(pr))
              .map((pr) => (
                <li key={pr}>
                  <span>{PROVIDER_NAMES[pr]}</span>
                  {p.connectedAccounts.includes(pr) ? (
                    <button className="btn secondary small" type="button" onClick={() => disconnect(pr)}>
                      Disconnect
                    </button>
                  ) : (
                    <a className="btn secondary small" href={`/api/v1/auth/oauth/${pr}/start?connect=true`}>
                      Connect
                    </a>
                  )}
                </li>
              ))}
          </ul>
        </section>
      )}

      <form className="panel form" onSubmit={submitPw}>
        <h2>{p.hasPassword ? 'Change password' : 'Set a password'}</h2>
        {!p.hasPassword && <p className="muted">You log in with {p.connectedAccounts.map((c) => PROVIDER_NAMES[c]).join(' or ')}. Add a password if you'd also like to log in with your email.</p>}
        {p.hasPassword && (
          <Field label="Current password" error={error?.fields.currentPassword}>
            {(fp) => (
              <input {...fp} type="password" autoComplete="current-password" value={pw.currentPassword} onChange={(e) => setPw({ ...pw, currentPassword: e.target.value })} />
            )}
          </Field>
        )}
        <Field label="New password" hint="At least 10 characters." error={error?.fields.newPassword}>
          {(fp) => (
            <input {...fp} type="password" autoComplete="new-password" value={pw.newPassword} onChange={(e) => setPw({ ...pw, newPassword: e.target.value })} />
          )}
        </Field>
        <div>
          <button className="btn" disabled={changePw.isPending || (p.hasPassword && !pw.currentPassword) || pw.newPassword.length < 10}>
            {p.hasPassword ? 'Change password' : 'Set password'}
          </button>
        </div>
      </form>

      <section className="panel">
        <h2>Your data</h2>
        <p className="muted">
          Download everything we hold about you, or delete your account. See the <Link to="/privacy">privacy notice</Link> for details.
        </p>
        <div className="row">
          <a className="btn secondary" href="/api/v1/me/export" download="my-summit-data.json">
            Download my data
          </a>
          <button className="btn danger" type="button" onClick={deleteAccount}>
            Delete my account
          </button>
        </div>
      </section>

      <div>
        <button className="btn secondary" onClick={onLogout}>
          Log out
        </button>
      </div>
    </div>
  );
}
