// Shapes returned by the Spring Boot API. Keep in sync with the *Dtos / *View records in the backend.

export type EventStatus = 'DRAFT' | 'PUBLISHED';

export interface EventView {
  id: string;
  slug: string;
  name: string;
  tagline: string | null;
  description: string | null;
  venueName: string | null;
  venueAddress: string | null;
  startsAt: string | null;
  endsAt: string | null;
  timezone: string;
  status: EventStatus;
}

export interface RoomView {
  id: string;
  name: string;
  sortOrder: number;
}

export type SessionType = 'TALK' | 'KEYNOTE' | 'PANEL' | 'WORKSHOP' | 'BREAK' | 'NETWORKING' | 'OTHER';
export type SessionStatus = 'SCHEDULED' | 'CANCELLED';

export interface SpeakerSummary {
  id: string;
  name: string;
  company: string | null;
  photoUrl: string | null;
}

export interface SessionView {
  id: string;
  title: string;
  description: string | null;
  type: SessionType;
  startsAt: string;
  endsAt: string | null;
  status: SessionStatus;
  room: RoomView | null;
  speakers: SpeakerSummary[];
}

export interface SpeakerView {
  id: string;
  name: string;
  headline: string | null;
  company: string | null;
  bio: string | null;
  photoUrl: string | null;
  linkedinUrl: string | null;
  websiteUrl: string | null;
  published: boolean;
  sortOrder: number;
}

export interface SpeakerDetail {
  speaker: SpeakerView;
  eventSlug: string;
  sessions: { id: string; title: string; startsAt: string; endsAt: string | null; roomName: string | null }[];
}

export interface SponsorView {
  id: string;
  name: string;
  tier: string;
  tierRank: number;
  description: string | null;
  logoUrl: string | null;
  websiteUrl: string | null;
  sortOrder: number;
}

export type Priority = 'NORMAL' | 'URGENT';

export interface AnnouncementView {
  id: string;
  title: string;
  body: string | null;
  priority: Priority;
  pinned: boolean;
  publishedAt: string;
  updatedAt: string;
}

export interface UserView {
  id: string;
  email: string;
  displayName: string;
  platformAdmin: boolean;
  organiserOf: string[];
}

export type MemberRole = 'ATTENDEE' | 'SPEAKER' | 'SPONSOR' | 'ORGANISER';

export interface ProfileView {
  id: string;
  email: string;
  displayName: string;
  platformAdmin: boolean;
  marketingConsent: boolean;
  createdAt: string;
  memberships: { id: string; eventId: string; eventSlug: string | null; eventName: string | null; role: MemberRole }[];
}

export interface MemberView {
  id: string;
  userId: string;
  email: string;
  displayName: string;
  role: MemberRole;
  createdAt: string;
}

export interface ApiErrorBody {
  status: number;
  code: string;
  message: string;
  fields: Record<string, string> | null;
}
