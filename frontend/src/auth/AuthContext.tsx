import { createContext, useCallback, useContext, useMemo, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/http';
import type { UserView } from '../api/types';

interface AuthState {
  user: UserView | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<UserView>;
  register: (input: {
    email: string;
    password: string;
    displayName: string;
    acceptPrivacy: boolean;
    marketingOptIn: boolean;
  }) => Promise<UserView>;
  logout: () => Promise<void>;
  refresh: () => Promise<unknown>;
  canManage: (eventId: string) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const client = useQueryClient();
  const session = useQuery({
    queryKey: ['auth-session'],
    queryFn: () => api<{ user: UserView | null }>('/api/v1/auth/session'),
    staleTime: 5 * 60_000,
    retry: 1,
  });

  const setUser = useCallback(
    (user: UserView | null) => {
      client.setQueryData(['auth-session'], { user });
      // Personal data from a previous user must never leak into the next session.
      client.removeQueries({ queryKey: ['me'] });
      client.removeQueries({ queryKey: ['schedule'] });
      client.removeQueries({ queryKey: ['admin'] });
    },
    [client],
  );

  const value = useMemo<AuthState>(() => {
    const user = session.data?.user ?? null;
    return {
      user,
      loading: session.isPending,
      login: async (email, password) => {
        const u = await api<UserView>('/api/v1/auth/login', { method: 'POST', body: { email, password } });
        setUser(u);
        return u;
      },
      register: async (input) => {
        const u = await api<UserView>('/api/v1/auth/register', { method: 'POST', body: input });
        setUser(u);
        return u;
      },
      logout: async () => {
        await api<void>('/api/v1/auth/logout', { method: 'POST' });
        setUser(null);
      },
      refresh: () => session.refetch(),
      canManage: (eventId) => !!user && (user.platformAdmin || user.organiserOf.includes(eventId)),
    };
  }, [session, setUser]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
