import { useQuery } from '@tanstack/react-query';
import { api } from './http';
import type {
  AnnouncementView,
  EventView,
  SessionView,
  SpeakerDetail,
  SpeakerView,
  SponsorView,
} from './types';

// Public data. Agenda/speakers/partners rarely change, so they're cached and kept in the browser
// (see lib/offline) to survive patchy venue Wi-Fi. Announcements are polled.

const ANNOUNCEMENT_POLL_MS = 30_000;

export const usePublishedEvents = () =>
  useQuery({ queryKey: ['events'], queryFn: () => api<EventView[]>('/api/v1/events') });

export const useEvent = (slug: string) =>
  useQuery({ queryKey: ['event', slug], queryFn: () => api<EventView>(`/api/v1/events/${slug}`) });

export const useAgenda = (slug: string) =>
  useQuery({
    queryKey: ['agenda', slug],
    queryFn: () => api<SessionView[]>(`/api/v1/events/${slug}/sessions`),
    refetchInterval: 5 * 60_000,
  });

export const useSession = (id: string) =>
  useQuery({ queryKey: ['session', id], queryFn: () => api<SessionView>(`/api/v1/sessions/${id}`) });

export const useSpeakers = (slug: string) =>
  useQuery({ queryKey: ['speakers', slug], queryFn: () => api<SpeakerView[]>(`/api/v1/events/${slug}/speakers`) });

export const useSpeaker = (id: string) =>
  useQuery({ queryKey: ['speaker', id], queryFn: () => api<SpeakerDetail>(`/api/v1/speakers/${id}`) });

export const useSponsors = (slug: string) =>
  useQuery({ queryKey: ['sponsors', slug], queryFn: () => api<SponsorView[]>(`/api/v1/events/${slug}/sponsors`) });

export const useAnnouncements = (slug: string) =>
  useQuery({
    queryKey: ['announcements', slug],
    queryFn: () => api<AnnouncementView[]>(`/api/v1/events/${slug}/announcements`),
    refetchInterval: ANNOUNCEMENT_POLL_MS,
    refetchIntervalInBackground: false,
  });
