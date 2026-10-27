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
  stageId: number;
  groupId: number | null;
  knockoutTieId: number | null;
  roundNo: number | null;
  homeTeamId: number;
  awayTeamId: number;
  kickoffAt: string | null;
  venue: string | null;
  referee: string | null;
  status: FixtureStatus;
  homeScore: number | null;
  awayScore: number | null;
  homePenaltyScore: number | null;
  awayPenaltyScore: number | null;
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
  referee: string | null;
  status: FixtureStatus;
  homeScore: number | null;
  awayScore: number | null;
  homePenaltyScore: number | null;
  awayPenaltyScore: number | null;
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

export type CompetitionType = "LEAGUE" | "CUP";
export type CompetitionStatus = "ACTIVE" | "INACTIVE";
export type StageType = "LEAGUE" | "GROUP" | "KNOCKOUT";
export type StageStatus = "DRAFT" | "ACTIVE" | "FINISHED";
export type TieStatus = "PENDING" | "READY" | "FINISHED" | "CANCELLED";

export interface Competition {
  id: number;
  name: string;
  type: CompetitionType;
  description: string | null;
  status: CompetitionStatus;
}

export interface Stage {
  id: number;
  seasonId: number;
  name: string;
  type: StageType;
  sortOrder: number;
  status: StageStatus;
}

export interface CompetitionGroup {
  id: number;
  stageId: number;
  name: string;
  sortOrder: number;
}

export interface BracketTeamRef {
  id: number;
  name: string;
  shortName: string | null;
}

export interface BracketTieView {
  id: number;
  bracketPosition: number;
  homeTeam: BracketTeamRef | null;
  awayTeam: BracketTeamRef | null;
  winnerTeam: BracketTeamRef | null;
  homeScore: number | null;
  awayScore: number | null;
  homePenaltyScore: number | null;
  awayPenaltyScore: number | null;
  status: TieStatus;
  fixtureId: number | null;
  thirdPlace: boolean;
}

export interface BracketRoundView {
  id: number;
  roundNo: number;
  name: string;
  ties: BracketTieView[];
}

export interface BracketView {
  stageId: number;
  stageName: string;
  rounds: BracketRoundView[];
  thirdPlace: BracketTieView | null;
  champion: BracketTeamRef | null;
}


export type RatingStatus = "VISIBLE" | "PENDING" | "REJECTED" | "HIDDEN";
export type SensitiveTermAction = "BLOCK" | "REVIEW";

export interface PlayerRatingSummary {
  playerId: number;
  playerName: string;
  jerseyNo: number | null;
  teamId: number;
  teamName: string;
  averageScore: number;
  ratingCount: number;
  star1Count: number;
  star2Count: number;
  star3Count: number;
  star4Count: number;
  star5Count: number;
}

export interface RatingComment {
  id: number;
  nickname: string;
  stars: number;
  score: number;
  comment: string;
  createdAt: string;
}

export interface RatingCommentPage {
  averageScore: number;
  ratingCount: number;
  page: number;
  size: number;
  total: number;
  comments: RatingComment[];
}

export interface PlayerSeasonRatingSummary {
  playerId: number;
  seasonId: number | null;
  averageScore: number;
  ratingCount: number;
}

export interface OwnRating {
  rated: boolean;
  id: number | null;
  stars: number | null;
  score: number | null;
  comment: string | null;
  status: RatingStatus | null;
  moderationReason: string | null;
  canEdit: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface RatingSubmission {
  id: number;
  stars: number;
  score: number;
  status: RatingStatus;
  moderationReason: string | null;
  canEdit: boolean;
}

export interface RatingModerationItem {
  id: number;
  fixtureId: number;
  playerId: number;
  playerName: string;
  teamName: string;
  nickname: string;
  stars: number;
  comment: string | null;
  status: RatingStatus;
  moderationReason: string | null;
  reporterCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface RatingModerationPage {
  total: number;
  items: RatingModerationItem[];
}

export interface SensitiveTerm {
  id: number;
  term: string;
  category: string;
  action: SensitiveTermAction;
  enabled: boolean;
  createdBy: number;
  createdAt: string;
  updatedAt: string;
}


export interface RefereeRatingSummary {
  refereeName: string | null;
  averageScore: number;
  ratingCount: number;
  star1Count: number;
  star2Count: number;
  star3Count: number;
  star4Count: number;
  star5Count: number;
}

export interface RefereeOwnRating {
  rated: boolean;
  id: number | null;
  stars: number | null;
  score: number | null;
  canEdit: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface RefereeRatingSubmission {
  id: number;
  stars: number;
  score: number;
  canEdit: boolean;
}
