import { request } from "./http";
import type { DashboardView } from "../types/domain";

export function fetchManagementDashboard(): Promise<DashboardView> {
  return request<DashboardView>("/api/v1/manage/dashboard");
}