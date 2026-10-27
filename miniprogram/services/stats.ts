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
