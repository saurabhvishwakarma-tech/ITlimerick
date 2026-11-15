import type { QueryClient } from '@tanstack/react-query';

// Keeps the last successful copy of public event data in localStorage, so the agenda still shows
// if the venue Wi-Fi drops or the server has a hiccup. Personal data is never stored here.

const PREFIX = 'summit-cache:';
const CACHEABLE = new Set(['events', 'event', 'agenda', 'speakers', 'sponsors', 'announcements']);

function storageKey(queryKey: readonly unknown[]): string {
  return PREFIX + JSON.stringify(queryKey);
}

export function persistPublicQueries(client: QueryClient): void {
  client.getQueryCache().subscribe((event) => {
    const query = event.query;
    if (event.type !== 'updated' || query.state.status !== 'success') return;
    if (!CACHEABLE.has(String(query.queryKey[0]))) return;
    try {
      localStorage.setItem(storageKey(query.queryKey), JSON.stringify(query.state.data));
    } catch {
      // Storage full or disabled (private browsing): the app still works, just without the offline copy.
    }
  });
}

export function restorePublicQueries(client: QueryClient): void {
  try {
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (!key || !key.startsWith(PREFIX)) continue;
      const queryKey = JSON.parse(key.slice(PREFIX.length)) as unknown[];
      const data = JSON.parse(localStorage.getItem(key) || 'null');
      if (data !== null) {
        // updatedAt 0 marks it stale, so fresh data is fetched straight away when online.
        client.setQueryData(queryKey, data, { updatedAt: 0 });
      }
    }
  } catch {
    // Ignore corrupt or unavailable storage.
  }
}
