import { request } from "./http";
import type { IdResponse } from "../types/api";
import type { Competition, CompetitionGroup, CompetitionStatus, CompetitionType, DisciplineRule, Season, SeasonStatus, Stage, StageStatus, StageType } from "../types/domain";

export function fetchAllCompetitions(): Promise<Competition[]> { return request("/api/v1/admin/competitions"); }
export function createCompetition(name: string, type: CompetitionType, description?: string): Promise<IdResponse> {
  return request("/api/v1/admin/competitions", { method: "POST", data: { name, type, description } });
}
export function fetchDisciplineRule(competitionId: number): Promise<DisciplineRule> {
  return request(`/api/v1/admin/competitions/${competitionId}/discipline-rule`);
}
export function updateDisciplineRule(competitionId: number, threshold: number, redMatches: number,
                                    cardsCrossStages: boolean): Promise<DisciplineRule> {
  return request(`/api/v1/admin/competitions/${competitionId}/discipline-rule`, {
    method: "PUT", data: { yellowSuspensionThreshold: threshold, redSuspensionMatches: redMatches, cardsCrossStages }
  });
}
export function fetchAdminSeasons(competitionId: number): Promise<Season[]> {
  return request(`/api/v1/admin/seasons?competitionId=${competitionId}`);
}
export function createSeason(competitionId: number, name: string, status: SeasonStatus): Promise<IdResponse> {
  return request("/api/v1/admin/seasons", { method: "POST", data: { competitionId, name, status } });
}
export function createStage(seasonId: number, name: string, type: StageType): Promise<IdResponse> {
  return request("/api/v1/admin/stages", { method: "POST", data: { seasonId, name, type, sortOrder: 0, status: "ACTIVE" } });
}
export function fetchAdminGroups(stageId: number): Promise<CompetitionGroup[]> { return request(`/api/v1/groups?stageId=${stageId}`); }
export function createGroup(stageId: number, name: string): Promise<IdResponse> {
  return request(`/api/v1/admin/stages/${stageId}/groups`, { method: "POST", data: { name, sortOrder: 0 } });
}
export function addGroupTeam(groupId: number, teamId: number, seedNo?: number): Promise<void> {
  return request(`/api/v1/admin/groups/${groupId}/teams`, { method: "POST", data: { teamId, seedNo } });
}
