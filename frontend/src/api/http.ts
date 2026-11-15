import type { ApiErrorBody } from './types';

/** An error from the API, with the server's user-facing message and any per-field messages. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fields: Record<string, string>;

  constructor(status: number, body: Partial<ApiErrorBody> | null) {
    super(body?.message || defaultMessage(status));
    this.status = status;
    this.code = body?.code || 'ERROR';
    this.fields = body?.fields || {};
  }
}

function defaultMessage(status: number): string {
  if (status === 0) return "Can't reach the server. Check your connection and try again.";
  if (status === 401) return 'Please log in to continue.';
  if (status === 403) return "You don't have permission to do that.";
  if (status === 404) return "We couldn't find that.";
  if (status === 429) return 'Too many attempts. Please wait a few minutes.';
  return 'Something went wrong. Please try again.';
}

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((c) => c.startsWith(name + '='));
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
}

/** Makes sure the XSRF-TOKEN cookie exists before a state-changing request. */
async function csrfToken(forceRefresh = false): Promise<string> {
  let token = forceRefresh ? null : readCookie('XSRF-TOKEN');
  if (!token) {
    const res = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' });
    const body = (await res.json()) as { token: string };
    token = readCookie('XSRF-TOKEN') || body.token;
  }
  return token;
}

export async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const send = async (forceRefreshCsrf: boolean): Promise<Response> => {
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (options.body !== undefined) headers['Content-Type'] = 'application/json';
    if (!SAFE_METHODS.has(method)) headers['X-XSRF-TOKEN'] = await csrfToken(forceRefreshCsrf);
    return fetch(path, {
      method,
      headers,
      credentials: 'same-origin',
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  };

  let res: Response;
  try {
    res = await send(false);
    // A stale CSRF token (e.g. after logging out in another tab) gives 403: refresh it once and retry.
    if (res.status === 403 && !SAFE_METHODS.has(method)) {
      res = await send(true);
    }
  } catch {
    throw new ApiError(0, null);
  }

  if (res.status === 204) return undefined as T;
  const text = await res.text();
  const data = text ? safeJson(text) : null;
  if (!res.ok) throw new ApiError(res.status, data as Partial<ApiErrorBody> | null);
  return data as T;
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}
