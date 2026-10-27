import { ApiError, request } from "./http";
import type { IdResponse } from "../types/api";
import type { FixtureDetail, FixtureSummary, Season, SeasonStatus, TeamSummary } from "../types/domain";

export function fetchPublicFixtures(seasonId: number): Promise<FixtureSummary[]> {
  return request<FixtureSummary[]>(`/api/v1/fixtures?seasonId=${seasonId}`);
}

export function fetchFixtureDetail(fixtureId: number): Promise<FixtureDetail> {
  return request<FixtureDetail>(`/api/v1/fixtures/${fixtureId}`);
}

export type CreateSeasonPayload = {
  name: string;
  startDate?: string | null;
  endDate?: string | null;
  status: SeasonStatus;
};

export function fetchAdminSeasons(): Promise<Season[]> {
  return request<Season[]>("/api/v1/admin/seasons");
}

export function createSeason(payload: CreateSeasonPayload): Promise<IdResponse> {
  if (!payload.name || !payload.name.trim()) {
    return Promise.reject(new ApiError(400, "VALIDATION_FAILED", "请输入赛季名称"));
  }
  return request<IdResponse>("/api/v1/admin/seasons", {
    method: "POST",
    data: { ...payload, name: payload.name.trim() }
  });
}

export function fetchSeasonTeams(seasonId: number): Promise<TeamSummary[]> {
  return request<TeamSummary[]>(`/api/v1/admin/seasons/${seasonId}/teams`);
}

export function addSeasonTeam(seasonId: number, teamId: number): Promise<void> {
  return request<void>(`/api/v1/admin/seasons/${seasonId}/teams/${teamId}`, { method: "POST" });
}

export function removeSeasonTeam(seasonId: number, teamId: number): Promise<void> {
  return request<void>(`/api/v1/admin/seasons/${seasonId}/teams/${teamId}`, { method: "DELETE" });
}
