import { useCallback, useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/http';
import { useAuth } from '../auth/AuthContext';

// "My schedule" works without an account: bookmarks live in this browser. Once someone logs in,
// those bookmarks are copied to their account and synced across their devices.

const localKey = (slug: string) => `summit-schedule:${slug}`;

function readLocal(slug: string): string[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(localKey(slug)) || '[]');
    return Array.isArray(parsed) ? parsed.filter((x) => typeof x === 'string') : [];
  } catch {
    return [];
  }
}

function writeLocal(slug: string, ids: string[]) {
  try {
    if (ids.length) localStorage.setItem(localKey(slug), JSON.stringify(ids));
    else localStorage.removeItem(localKey(slug));
  } catch {
    // ignore
  }
}

export function useSchedule(slug: string) {
  const { user } = useAuth();
  const client = useQueryClient();
  const [localIds, setLocalIds] = useState<string[]>(() => readLocal(slug));
  const migrated = useRef(false);

  const remote = useQuery({
    queryKey: ['schedule', slug, user?.id],
    queryFn: () => api<string[]>(`/api/v1/me/schedule?event=${encodeURIComponent(slug)}`),
    enabled: !!user,
  });

  // After login, move this browser's bookmarks onto the account.
  useEffect(() => {
    if (!user || migrated.current) return;
    migrated.current = true;
    const pending = readLocal(slug);
    if (!pending.length) return;
    Promise.allSettled(pending.map((id) => api(`/api/v1/me/schedule/${id}`, { method: 'PUT' }))).then(() => {
      writeLocal(slug, []);
      setLocalIds([]);
      client.invalidateQueries({ queryKey: ['schedule', slug] });
    });
  }, [user, slug, client]);

  const mutation = useMutation({
    mutationFn: ({ id, saved }: { id: string; saved: boolean }) =>
      api<void>(`/api/v1/me/schedule/${id}`, { method: saved ? 'PUT' : 'DELETE' }),
    onMutate: async ({ id, saved }) => {
      const key = ['schedule', slug, user?.id];
      await client.cancelQueries({ queryKey: key });
      const previous = client.getQueryData<string[]>(key) ?? [];
      client.setQueryData<string[]>(key, saved ? [...new Set([...previous, id])] : previous.filter((x) => x !== id));
      return { previous, key };
    },
    onError: (_err, _vars, ctx) => {
      if (ctx) client.setQueryData(ctx.key, ctx.previous);
    },
  });

  const ids = user ? (remote.data ?? []) : localIds;

  const toggle = useCallback(
    (id: string) => {
      const saved = !ids.includes(id);
      if (user) {
        mutation.mutate({ id, saved });
      } else {
        const next = saved ? [...ids, id] : ids.filter((x) => x !== id);
        writeLocal(slug, next);
        setLocalIds(next);
      }
    },
    [ids, user, mutation, slug],
  );

  return { ids: new Set(ids), toggle, syncedToAccount: !!user };
}
