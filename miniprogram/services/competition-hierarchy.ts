import { request } from "./http";
import type { Competition, CompetitionGroup, Season, Stage } from "../types/domain";

export function fetchCompetitions(): Promise<Competition[]> {
  return request<Competition[]>("/api/v1/competitions");
}

export function fetchSeasonsByCompetition(competitionId: number): Promise<Season[]> {
  return request<Season[]>(`/api/v1/seasons?competitionId=${competitionId}`);
}

export function fetchStages(seasonId: number): Promise<Stage[]> {
  return request<Stage[]>(`/api/v1/stages?seasonId=${seasonId}`);
}

export function fetchGroups(stageId: number): Promise<CompetitionGroup[]> {
  return request<CompetitionGroup[]>(`/api/v1/groups?stageId=${stageId}`);
}
