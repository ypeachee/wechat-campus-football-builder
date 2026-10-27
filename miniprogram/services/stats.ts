import { request } from "./http";
import type { ScorerRow, StandingRow } from "../types/domain";

export function fetchStandings(seasonId: number): Promise<StandingRow[]> {
  return request<StandingRow[]>(`/api/v1/standings?seasonId=${seasonId}`);
}

export function fetchScorers(seasonId: number): Promise<ScorerRow[]> {
  return request<ScorerRow[]>(`/api/v1/stats/scorers?seasonId=${seasonId}`);
}
