import { request } from "./http";
import type { AuditLogRow } from "../types/domain";

export function fetchAuditLogs(): Promise<AuditLogRow[]> {
  return request<AuditLogRow[]>("/api/v1/manage/audit-logs");
}
