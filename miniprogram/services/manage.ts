import { request } from "./http";
import type { IdResponse } from "../types/api";
import type { PlayerStatus, PlayerSummary, TeamStatus, TeamSummary } from "../types/domain";

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
