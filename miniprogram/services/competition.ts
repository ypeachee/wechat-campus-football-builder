import { request } from "./http";
import type { FixtureDetail, FixtureSummary } from "../types/domain";

export function fetchPublicFixtures(seasonId: number): Promise<FixtureSummary[]> {
  return request<FixtureSummary[]>(`/api/v1/fixtures?seasonId=${seasonId}`);
}

export function fetchFixtureDetail(fixtureId: number): Promise<FixtureDetail> {
  return request<FixtureDetail>(`/api/v1/fixtures/${fixtureId}`);
}
