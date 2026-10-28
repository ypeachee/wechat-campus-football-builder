import { request } from "./http";
import type { DisciplineCenter } from "../types/domain";

export function fetchDisciplineCenter(seasonId: number, teamId?: number): Promise<DisciplineCenter> {
  const suffix = teamId ? `&teamId=${teamId}` : "";
  return request<DisciplineCenter>(`/api/v1/manage/discipline?seasonId=${seasonId}${suffix}`);
}