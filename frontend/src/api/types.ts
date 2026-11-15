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
  hackathonEnabled: boolean;
  teamMaxSize: number;
  votingOpensAt: string | null;
  votingClosesAt: string | null;
  votingResultsPublic: boolean;
  chatEnabled: boolean;
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

export type Emoji = 'HEART' | 'THUMBS_UP' | 'PARTY' | 'CLAP';

export interface AnnouncementView {
  id: string;
  title: string;
  body: string | null;
  priority: Priority;
  pinned: boolean;
  publishedAt: string;
  updatedAt: string;
  reactions: Partial<Record<Emoji, number>>;
  myReactions: Emoji[];
}

// ---- Hackathon ----

export interface TeamView {
  id: string;
  name: string;
  projectName: string | null;
  description: string | null;
  sector: string | null;
  logoUrl: string | null;
  projectUrl: string | null;
  shortlisted: boolean;
  memberCount: number;
  memberNames: string[];
}

export type TeamRole = 'LEAD' | 'MEMBER';

export interface TeamAdminView {
  id: string;
  name: string;
  projectName: string | null;
  description: string | null;
  sector: string | null;
  logoUrl: string | null;
  projectUrl: string | null;
  shortlisted: boolean;
  joinCode: string;
  members: { userId: string; displayName: string; email: string; role: TeamRole }[];
}

export interface ParticipantView {
  userId: string;
  displayName: string;
  email: string;
  skills: string | null;
  lookingForTeam: boolean;
  teamId: string | null;
  teamName: string | null;
  joinedAt: string;
}

export interface MyHackathonView {
  participant: boolean;
  skills: string | null;
  lookingForTeam: boolean;
  team: TeamView | null;
  myRole: TeamRole | null;
  joinCode: string | null;
  teamMaxSize: number;
}

export type VotingStatus = 'NOT_SCHEDULED' | 'NOT_OPEN' | 'OPEN' | 'CLOSED';

export interface VotingView {
  status: VotingStatus;
  opensAt: string | null;
  closesAt: string | null;
  candidates: {
    teamId: string;
    name: string;
    projectName: string | null;
    description: string | null;
    logoUrl: string | null;
    sector: string | null;
    isMyTeam: boolean;
  }[];
  myVoteTeamId: string | null;
  results: { teamId: string; name: string; projectName: string | null; votes: number }[] | null;
  totalVotes: number;
}

export interface ChatMessageView {
  id: string;
  authorName: string;
  fromOrganiser: boolean;
  mine: boolean;
  body: string | null;
  removed: boolean;
  createdAt: string;
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
