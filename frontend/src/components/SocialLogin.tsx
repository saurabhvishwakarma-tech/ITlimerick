import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { api } from '../api/http';
import type { Provider } from '../api/types';
import { lastEventSlug } from './Shell';
import { Notice } from './ui';

export const PROVIDER_NAMES: Record<Provider, string> = { google: 'Google', linkedin: 'LinkedIn' };

export function useProviders() {
  return useQuery({
    queryKey: ['auth-providers'],
    queryFn: () => api<{ providers: Provider[] }>('/api/v1/auth/providers'),
    staleTime: 10 * 60_000,
  }).data?.providers ?? [];
}

const OAUTH_ERRORS: Record<string, string> = {
  cancelled: 'Sign-in was cancelled. You can try again, or use your email instead.',
  expired: 'That sign-in took too long or was opened in another browser. Please try again.',
  'no-email': "We couldn't get a confirmed email address from that account. Try another way to sign in.",
  'linked-elsewhere': 'That account is already connected to a different login here.',
  unavailable: "That sign-in option isn't switched on yet. Use your email instead.",
  failed: "Sign-in didn't work this time. Please try again in a moment.",
};

/** Shows why a Google/LinkedIn sign-in failed, when the server sent us back with ?oauthError=… */
export function OAuthErrorNotice() {
  const [params] = useSearchParams();
  const code = params.get('oauthError');
  if (!code) return null;
  return <Notice kind="error">{OAUTH_ERRORS[code] ?? OAUTH_ERRORS.failed}</Notice>;
}

/** Where to land after signing in: the ?next= path if it's internal, otherwise the last event viewed. */
function returnTo(next: string | null): string {
  if (next && next.startsWith('/') && !next.startsWith('//')) return next;
  const slug = lastEventSlug();
  return slug ? `/e/${slug}` : '/';
}

export function SocialButtons() {
  const providers = useProviders();
  const [params] = useSearchParams();
  if (providers.length === 0) return null;
  const target = encodeURIComponent(returnTo(params.get('next')));
  return (
    <div className="social stack">
      {providers.map((p) => (
        <a key={p} className={`btn social-btn is-${p}`} href={`/api/v1/auth/oauth/${p}/start?returnTo=${target}`}>
          Continue with {PROVIDER_NAMES[p]}
        </a>
      ))}
      <p className="small muted">
        By continuing you accept the <Link to="/privacy">privacy notice</Link>. We only get your name and email address.
      </p>
      <p className="divider-text" aria-hidden="true">
        or use your email
      </p>
    </div>
  );
}
