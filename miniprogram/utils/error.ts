import { ApiError } from "../services/http";

export function errorMessage(error: unknown, fallback = "加载失败，请稍后重试"): string {
  if (error instanceof ApiError && error.code === "FIXTURE_LOCKED") {
    return "比赛已锁定，请管理员先解锁";
  }
  return error instanceof ApiError ? error.message : fallback;
}
