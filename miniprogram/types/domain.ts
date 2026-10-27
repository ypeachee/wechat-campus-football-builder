export type UserRole = "ADMIN" | "TEAM_MANAGER" | "VIEWER";
export type UserStatus = "ACTIVE" | "DISABLED";
export type TeamStatus = "ACTIVE" | "INACTIVE";
export type PlayerStatus = "ACTIVE" | "INACTIVE";
export type SeasonStatus = "DRAFT" | "ACTIVE" | "FINISHED";
export type FixtureStatus = "DRAFT" | "SCHEDULED" | "LIVE" | "FINISHED" | "CANCELLED";
export type EventType = "GOAL" | "OWN_GOAL" | "YELLOW_CARD" | "RED_CARD";
export type AuditAction = "CREATE" | "UPDATE" | "DELETE" | "GRANT" | "REVOKE" | "SCORE_UPDATE" | "FINISH" | "LOCK" | "UNLOCK";

export interface Session {
  token: string;
  userId: number;
  nickname: string | null;
  role: UserRole;
  managedTeamIds: number[];
}

export interface Season {
  id: number;
  name: string;
  startDate: string | null;
  endDate: string | null;
  status: SeasonStatus;
}

export interface TeamSummary {
  id: number;
  name: string;
  shortName: string | null;
  logoUrl: string | null;
  coachName: string | null;
  homeVenue: string | null;
  status: TeamStatus;
}

export interface PlayerSummary {
  id: number;
  teamId: number;
  name: string;
  jerseyNo: number | null;
  position: string | null;
  birthDate: string | null;
  photoUrl: string | null;
  status: PlayerStatus;
}

export interface FixtureSummary {
  id: number;
  seasonId: number;
  roundNo: number | null;
  homeTeamId: number;
  awayTeamId: number;
  kickoffAt: string | null;
  venue: string | null;
  status: FixtureStatus;
  homeScore: number | null;
  awayScore: number | null;
  dataLocked: boolean;
}

export interface TeamRef {
  id: number;
  name: string;
  shortName: string | null;
}

export interface FixtureEvent {
  id: number;
  teamId: number;
  playerId: number;
  eventType: EventType;
  relatedPlayerId: number | null;
  minute: number;
  stoppageMinute: number;
  note: string | null;
}

export interface FixtureAppearance {
  id: number;
  teamId: number;
  playerId: number;
  starter: boolean;
  minutesPlayed: number | null;
}

export interface FixtureDetail {
  id: number;
  seasonId: number;
  roundNo: number | null;
  homeTeam: TeamRef;
  awayTeam: TeamRef;
  kickoffAt: string | null;
  venue: string | null;
  status: FixtureStatus;
  homeScore: number | null;
  awayScore: number | null;
  dataLocked: boolean;
  events: FixtureEvent[];
  appearances: FixtureAppearance[];
}

export interface ScorerRow {
  playerId: number;
  playerName: string;
  teamId: number;
  teamName: string;
  goals: number;
}

export interface AssistRow {
  playerId: number;
  playerName: string;
  teamId: number;
  teamName: string;
  assists: number;
}

export interface AppearanceRow {
  playerId: number;
  playerName: string;
  teamId: number;
  teamName: string;
  appearances: number;
}

export interface StandingRow {
  teamId: number;
  teamName: string;
  played: number;
  won: number;
  drawn: number;
  lost: number;
  goalsFor: number;
  goalsAgainst: number;
  goalDifference: number;
  points: number;
}

export interface AuditLogRow {
  id: number;
  actorUserId: number;
  teamId: number | null;
  action: AuditAction;
  entityType: string;
  entityId: number;
  beforeJson: string | null;
  afterJson: string | null;
  createdAt: string;
}

export interface FixtureCard extends FixtureSummary {
  homeTeamName: string;
  awayTeamName: string;
  timeLabel: string;
}

export interface RankingDisplayItem {
  id: number;
  rank: number;
  name: string;
  teamName: string;
  value: number;
}
