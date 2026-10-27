import { request } from "./http";
import type { PlayerSummary, Season, TeamSummary } from "../types/domain";

export function fetchSeasons(): Promise<Season[]> {
  return request<Season[]>("/api/v1/seasons");
}

export function fetchTeams(): Promise<TeamSummary[]> {
  return request<TeamSummary[]>("/api/v1/teams");
}

export function fetchTeam(teamId: number): Promise<TeamSummary> {
  return request<TeamSummary>(`/api/v1/teams/${teamId}`);
}

export function fetchTeamPlayers(teamId: number): Promise<PlayerSummary[]> {
  return request<PlayerSummary[]>(`/api/v1/teams/${teamId}/players`);
}

export function fetchPlayer(playerId: number): Promise<PlayerSummary> {
  return request<PlayerSummary>(`/api/v1/players/${playerId}`);
}
