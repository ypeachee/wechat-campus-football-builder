import { ApiError, request } from "./http";
import type { IdResponse } from "../types/api";
import type { EventType, FixtureDetail, FixtureStatus, FixtureSummary, Season, SeasonStatus, TeamSummary } from "../types/domain";

export function fetchPublicFixtures(seasonId: number): Promise<FixtureSummary[]> {
  return request<FixtureSummary[]>(`/api/v1/fixtures?seasonId=${seasonId}`);
}

export function fetchPublicFixturesByStage(stageId: number, groupId?: number | null): Promise<FixtureSummary[]> {
  const group = groupId ? `&groupId=${groupId}` : "";
  return request<FixtureSummary[]>(`/api/v1/fixtures?stageId=${stageId}${group}`);
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

export type FixturePayload = {
  seasonId: number;
  roundNo?: number | null;
  homeTeamId: number;
  awayTeamId: number;
  kickoffAt?: string | null;
  venue?: string | null;
};

export function fetchAdminFixtures(seasonId: number): Promise<FixtureSummary[]> {
  return request<FixtureSummary[]>(`/api/v1/admin/fixtures?seasonId=${seasonId}`);
}

export function fetchAdminFixture(fixtureId: number): Promise<FixtureSummary> {
  return request<FixtureSummary>(`/api/v1/admin/fixtures/${fixtureId}`);
}

export function createFixture(payload: FixturePayload): Promise<IdResponse> {
  return request<IdResponse>("/api/v1/admin/fixtures", { method: "POST", data: payload });
}

export function updateFixture(fixtureId: number, payload: FixturePayload): Promise<void> {
  return request<void>(`/api/v1/admin/fixtures/${fixtureId}`, { method: "PUT", data: payload });
}

export function changeFixtureStatus(fixtureId: number, status: FixtureStatus): Promise<void> {
  return request<void>(`/api/v1/admin/fixtures/${fixtureId}/status`, {
    method: "PUT",
    data: { status }
  });
}

export function updateOfficialScore(fixtureId: number, homeScore: number, awayScore: number,
                                     homePenaltyScore?: number | null, awayPenaltyScore?: number | null): Promise<void> {
  return request<void>(`/api/v1/admin/fixtures/${fixtureId}/score`, {
    method: "PUT",
    data: { homeScore, awayScore, homePenaltyScore, awayPenaltyScore }
  });
}

export function finishFixture(fixtureId: number, homeScore: number, awayScore: number,
                              homePenaltyScore?: number | null, awayPenaltyScore?: number | null): Promise<void> {
  return request<void>(`/api/v1/admin/fixtures/${fixtureId}/finish`, {
    method: "POST",
    data: { homeScore, awayScore, homePenaltyScore, awayPenaltyScore }
  });
}

export function setFixtureLocked(fixtureId: number, locked: boolean): Promise<void> {
  return request<void>(`/api/v1/admin/fixtures/${fixtureId}/lock`, {
    method: "PUT",
    data: { locked }
  });
}

export function fetchBracket(stageId: number): Promise<import("../types/domain").BracketView> {
  return request(`/api/v1/brackets?stageId=${stageId}`);
}
