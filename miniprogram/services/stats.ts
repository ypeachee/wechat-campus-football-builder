import { request } from "./http";
import type { AppearanceRow, AssistRow, ScorerRow, StandingRow } from "../types/domain";

export function fetchStandings(seasonId: number): Promise<StandingRow[]> {
  return request<StandingRow[]>(`/api/v1/standings?seasonId=${seasonId}`);
}

export function fetchScorers(seasonId: number): Promise<ScorerRow[]> {
  return request<ScorerRow[]>(`/api/v1/stats/scorers?seasonId=${seasonId}`);
}

export function fetchAssists(seasonId: number): Promise<AssistRow[]> {
  return request<AssistRow[]>(`/api/v1/stats/assists?seasonId=${seasonId}`);
}

export function fetchAppearances(seasonId: number): Promise<AppearanceRow[]> {
  return request<AppearanceRow[]>(`/api/v1/stats/appearances?seasonId=${seasonId}`);
}

export interface StatsQuery {
  seasonId?: number;
  competitionId?: number;
  stageId?: number;
  groupId?: number;
}

function queryString(filter: StatsQuery): string {
  return Object.entries(filter)
    .filter(([, value]) => value !== undefined && value !== null && value > 0)
    .map(([key, value]) => `${key}=${value}`)
    .join("&");
}

export function fetchStandingsFiltered(filter: StatsQuery): Promise<StandingRow[]> {
  return request<StandingRow[]>(`/api/v1/standings?${queryString(filter)}`);
}
export function fetchScorersFiltered(filter: StatsQuery): Promise<ScorerRow[]> {
  return request<ScorerRow[]>(`/api/v1/stats/scorers?${queryString(filter)}`);
}
export function fetchAssistsFiltered(filter: StatsQuery): Promise<AssistRow[]> {
  return request<AssistRow[]>(`/api/v1/stats/assists?${queryString(filter)}`);
}
export function fetchAppearancesFiltered(filter: StatsQuery): Promise<AppearanceRow[]> {
  return request<AppearanceRow[]>(`/api/v1/stats/appearances?${queryString(filter)}`);
}
