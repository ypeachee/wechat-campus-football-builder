import { request } from "./http";
import type { IdResponse } from "../types/api";
import type { EventType, PlayerStatus, PlayerSuspension, PlayerSummary, SuspensionStatus, TeamManagerAssignment, TeamStatus, TeamSummary, UserRole } from "../types/domain";

export type CreateTeamPayload = {
  name: string;
  shortName?: string | null;
  logoUrl?: string | null;
  coachName?: string | null;
  homeVenue?: string | null;
};

export type UpdateTeamPayload = CreateTeamPayload & {
  status?: TeamStatus | null;
};

export type CreatePlayerPayload = {
  name: string;
  jerseyNo?: number | null;
  position?: string | null;
  birthDate?: string | null;
  photoUrl?: string | null;
};

export type UpdatePlayerPayload = CreatePlayerPayload & {
  status?: PlayerStatus | null;
};

export function createTeam(payload: CreateTeamPayload): Promise<IdResponse> {
  return request<IdResponse>("/api/v1/admin/teams", { method: "POST", data: payload });
}

export function updateManagedTeam(teamId: number, payload: UpdateTeamPayload): Promise<void> {
  return request<void>(`/api/v1/manage/teams/${teamId}`, { method: "PUT", data: payload });
}

export function createManagedPlayer(teamId: number, payload: CreatePlayerPayload): Promise<IdResponse> {
  return request<IdResponse>(`/api/v1/manage/teams/${teamId}/players`, { method: "POST", data: payload });
}

export function updateManagedPlayer(playerId: number, payload: UpdatePlayerPayload): Promise<void> {
  return request<void>(`/api/v1/manage/players/${playerId}`, { method: "PUT", data: payload });
}

export function fetchManagedTeamPlayers(teamId: number): Promise<PlayerSummary[]> {
  return request<PlayerSummary[]>(`/api/v1/manage/teams/${teamId}/players`);
}

export function fetchAdminTeams(): Promise<TeamSummary[]> {
  return request<TeamSummary[]>("/api/v1/admin/teams");
}

export function fetchAdminTeamPlayers(teamId: number): Promise<PlayerSummary[]> {
  return request<PlayerSummary[]>(`/api/v1/admin/teams/${teamId}/players`);
}

export function fetchTeamManagers(teamId: number): Promise<TeamManagerAssignment[]> {
  return request<TeamManagerAssignment[]>(`/api/v1/admin/teams/${teamId}/managers`);
}

export function grantTeamManager(teamId: number, userId: number,
                                role: Extract<UserRole, "TEAM_MANAGER" | "TEAM_LEADER">): Promise<void> {
  return request<void>(`/api/v1/admin/teams/${teamId}/managers`, {
    method: "POST",
    data: { userId, role }
  });
}

export function revokeTeamManager(teamId: number, userId: number): Promise<void> {
  return request<void>(`/api/v1/admin/teams/${teamId}/managers/${userId}`, { method: "DELETE" });
}

export function fetchTeamSuspensions(teamId: number, seasonId: number): Promise<PlayerSuspension[]> {
  return request<PlayerSuspension[]>(`/api/v1/manage/teams/${teamId}/suspensions?seasonId=${seasonId}`);
}

export function fetchFixtureSuspensions(fixtureId: number): Promise<PlayerSuspension[]> {
  return request<PlayerSuspension[]>(`/api/v1/manage/fixtures/${fixtureId}/suspensions`);
}

export function createSuspension(playerId: number, seasonId: number, matchesTotal: number,
                                 reason: string): Promise<IdResponse> {
  return request<IdResponse>("/api/v1/manage/suspensions", {
    method: "POST",
    data: { playerId, seasonId, matchesTotal, reason }
  });
}

export function updateSuspension(id: number, matchesTotal: number, status: SuspensionStatus): Promise<void> {
  return request<void>(`/api/v1/manage/suspensions/${id}`, {
    method: "PUT",
    data: { matchesTotal, status }
  });
}

export type LineupSubstitutePayload = {
  playerId: number;
  minutesPlayed: number | null;
};

export function replaceTeamLineup(fixtureId: number, teamId: number, starters: number[],
                                  substitutes: LineupSubstitutePayload[]): Promise<void> {
  return request<void>(`/api/v1/manage/fixtures/${fixtureId}/teams/${teamId}/appearances`, {
    method: "PUT",
    data: { starters, substitutes }
  });
}

export type AppearancePayload = {
  teamId?: number;
  playerId?: number;
  isStarter: boolean;
  minutesPlayed?: number | null;
};

export type EventPayload = {
  teamId?: number;
  playerId: number;
  eventType: EventType;
  relatedPlayerId?: number | null;
  minute: number;
  stoppageMinute: number;
  note?: string | null;
};

export function createAppearance(fixtureId: number, payload: AppearancePayload): Promise<IdResponse> {
  return request<IdResponse>(`/api/v1/manage/fixtures/${fixtureId}/appearances`, {
    method: "POST",
    data: payload
  });
}

export function updateAppearance(appearanceId: number, payload: AppearancePayload): Promise<void> {
  return request<void>(`/api/v1/manage/appearances/${appearanceId}`, { method: "PUT", data: payload });
}

export function deleteAppearance(appearanceId: number): Promise<void> {
  return request<void>(`/api/v1/manage/appearances/${appearanceId}`, { method: "DELETE" });
}

export function createEvent(fixtureId: number, payload: EventPayload): Promise<IdResponse> {
  return request<IdResponse>(`/api/v1/manage/fixtures/${fixtureId}/events`, {
    method: "POST",
    data: payload
  });
}

export function updateEvent(eventId: number, payload: EventPayload): Promise<void> {
  return request<void>(`/api/v1/manage/events/${eventId}`, { method: "PUT", data: payload });
}

export function deleteEvent(eventId: number): Promise<void> {
  return request<void>(`/api/v1/manage/events/${eventId}`, { method: "DELETE" });
}
