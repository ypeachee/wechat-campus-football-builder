import { request } from "./http";
import type { Season, TeamSummary } from "../types/domain";

export function fetchSeasons(): Promise<Season[]> {
  return request<Season[]>("/api/v1/seasons");
}

export function fetchTeams(): Promise<TeamSummary[]> {
  return request<TeamSummary[]>("/api/v1/teams");
}
