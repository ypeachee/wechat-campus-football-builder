import { request } from "./http";
import type { IdResponse } from "../types/api";
import type { BracketView } from "../types/domain";

export function fetchBracket(stageId: number, admin = false): Promise<BracketView> {
  return request(`${admin ? "/api/v1/admin/brackets" : "/api/v1/brackets"}?stageId=${stageId}`);
}
export function generateBracket(stageId: number, groupStageId: number, qualifiersPerGroup: number, thirdPlace: boolean): Promise<BracketView> {
  return request(`/api/v1/admin/stages/${stageId}/generate-bracket`, { method: "POST", data: { groupStageId, qualifiersPerGroup, thirdPlace } });
}
export function createTieFixture(tieId: number, kickoffAt: string | null, venue: string | null,
                                 referee: string | null): Promise<IdResponse> {
  return request(`/api/v1/admin/ties/${tieId}/fixture`, { method: "POST", data: { kickoffAt, venue, referee } });
}
