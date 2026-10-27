import { ApiError } from "../services/http";

export function errorMessage(error: unknown, fallback = "加载失败，请稍后重试"): string {
  return error instanceof ApiError ? error.message : fallback;
}
