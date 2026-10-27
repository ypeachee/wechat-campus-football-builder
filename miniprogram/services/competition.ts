import { request } from "./http";
import type { FixtureSummary } from "../types/domain";

export function fetchPublicFixtures(seasonId: number): Promise<FixtureSummary[]> {
  return request<FixtureSummary[]>(`/api/v1/fixtures?seasonId=${seasonId}`);
}
