import { getApiBaseUrl } from "../config/env";
import { clearSession, getSession } from "../state/session";
import type { ApiErrorBody } from "../types/api";

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly requestId: string = ""
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export interface RequestOptions {
  method?: WechatMiniprogram.RequestOption["method"];
  data?: unknown;
  auth?: boolean;
  headers?: Record<string, string>;
}

let unauthorizedHandler: () => void = () => undefined;
let unauthorizedNotified = false;

export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler;
  unauthorizedNotified = false;
}

export function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const session = getSession();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...options.headers
  };
  if (options.auth !== false && session?.token) {
    headers.Authorization = `Bearer ${session.token}`;
  }

  return new Promise<T>((resolve, reject) => {
    const requestOptions: WechatMiniprogram.RequestOption = {
      url: path.startsWith("http") ? path : `${getApiBaseUrl()}${path}`,
      method: options.method ?? "GET",
      data: options.data as WechatMiniprogram.RequestOption["data"],
      header: headers,
      timeout: 10000,
      success: (response) => {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          unauthorizedNotified = false;
          resolve(response.data as T);
          return;
        }
        const error = toApiError(response.statusCode, response.data);
        if (response.statusCode === 401 && options.auth !== false) {
          clearSession();
          if (!unauthorizedNotified) {
            unauthorizedNotified = true;
            unauthorizedHandler();
          }
        }
        reject(error);
      },
      fail: (failure) => {
        reject(new ApiError(0, "NETWORK_ERROR", failure.errMsg || "网络请求失败"));
      }
    };
    wx.request(requestOptions);
  });
}

function toApiError(status: number, data: unknown): ApiError {
  if (data && typeof data === "object") {
    const body = data as Partial<ApiErrorBody>;
    return new ApiError(
      status,
      body.code || `HTTP_${status}`,
      body.message || "请求失败",
      body.requestId || ""
    );
  }
  return new ApiError(status, `HTTP_${status}`, "请求失败");
}
