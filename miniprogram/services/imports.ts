import { getApiBaseUrl } from "../config/env";
import { ApiError } from "./http";
import { clearSession, getSession } from "../state/session";
import type { ApiErrorBody } from "../types/api";
import type { ImportPreview, ImportResult } from "../types/domain";

export type ImportKind = "teams" | "players";
export type ImportStage = "preview" | "commit";

export function uploadImportFile(kind: ImportKind, stage: ImportStage, filePath: string,
                                 overwrite: boolean, teamId?: number): Promise<ImportPreview | ImportResult> {
  const session = getSession();
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${getApiBaseUrl()}/api/v1/manage/import/${kind}/${stage}`,
      filePath,
      name: "file",
      formData: { overwrite: overwrite ? "true" : "false", ...(teamId ? { teamId: String(teamId) } : {}) },
      header: session?.token ? { Authorization: `Bearer ${session.token}` } : {},
      timeout: 30000,
      success: (response) => {
        const data = parseBody(response.data);
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(data as ImportPreview | ImportResult);
          return;
        }
        if (response.statusCode === 401) clearSession();
        reject(toError(response.statusCode, data));
      },
      fail: (failure) => reject(new ApiError(0, "NETWORK_ERROR", failure.errMsg || "文件上传失败"))
    });
  });
}

function parseBody(raw: string): unknown {
  try { return JSON.parse(raw); } catch { return raw; }
}

function toError(status: number, data: unknown): ApiError {
  if (data && typeof data === "object") {
    const body = data as Partial<ApiErrorBody>;
    return new ApiError(status, body.code || `HTTP_${status}`, body.message || "导入失败", body.requestId || "");
  }
  return new ApiError(status, `HTTP_${status}`, typeof data === "string" && data ? data : "导入失败");
}