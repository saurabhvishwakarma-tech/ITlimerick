import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import '@fontsource-variable/bricolage-grotesque';
import './styles.css';
import { ApiError } from './api/http';
import { AuthProvider } from './auth/AuthContext';
import { EventShell, PlainShell } from './components/Shell';
import { persistPublicQueries, restorePublicQueries } from './lib/offline';
import { AccountPage } from './pages/AccountPage';
import { AdminEvent, AdminHome } from './pages/admin/AdminPages';
import { AgendaPage } from './pages/AgendaPage';
import { TicketPage } from './pages/AttendeeExtras';
import { ForgotPasswordPage, LoginPage, RegisterPage, ResetPasswordPage } from './pages/AuthPages';
import { MorePage, TeamPage, TeamsPage, VotePage } from './pages/HackathonPages';
import { HomeRedirect, NotFoundPage, PrivacyPage } from './pages/MiscPages';
import { MySchedulePage, PartnersPage, SessionPage, SpeakerPage, SpeakersPage, UpdatesPage } from './pages/PublicPages';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      gcTime: 24 * 60 * 60_000,
      refetchOnWindowFocus: true,
      // Don't hammer the server for errors that won't fix themselves.
      retry: (count, error) => !(error instanceof ApiError && [401, 403, 404].includes(error.status)) && count < 2,
    },
  },
});
restorePublicQueries(queryClient);
persistPublicQueries(queryClient);

const router = createBrowserRouter([
  {
    path: '/e/:slug',
    element: <EventShell />,
    children: [
      { index: true, element: <AgendaPage /> },
      { path: 'sessions/:sessionId', element: <SessionPage /> },
      { path: 'speakers', element: <SpeakersPage /> },
      { path: 'speakers/:speakerId', element: <SpeakerPage /> },
      { path: 'partners', element: <PartnersPage /> },
      { path: 'updates', element: <UpdatesPage /> },
      { path: 'my-schedule', element: <MySchedulePage /> },
      { path: 'teams', element: <TeamsPage /> },
      { path: 'teams/:teamId', element: <TeamPage /> },
      { path: 'vote', element: <VotePage /> },
      { path: 'more', element: <MorePage /> },
      { path: 'ticket', element: <TicketPage /> },
    ],
  },
  {
    element: <PlainShell />,
    children: [
      { path: '/', element: <HomeRedirect /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
      { path: '/forgot-password', element: <ForgotPasswordPage /> },
      { path: '/reset-password', element: <ResetPasswordPage /> },
      { path: '/account', element: <AccountPage /> },
      { path: '/privacy', element: <PrivacyPage /> },
      { path: '/admin', element: <AdminHome /> },
      { path: '/admin/events/:eventId/:tab?', element: <AdminEvent /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
);
