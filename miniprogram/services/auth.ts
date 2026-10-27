import { ApiError, request } from "./http";
import { clearSession, getSession, saveSession } from "../state/session";
import type { AuthResponse, MeResponse } from "../types/api";
import type { Session } from "../types/domain";

export function loginWithWechatCode(code: string): Promise<AuthResponse> {
  return request<AuthResponse>("/api/v1/auth/wechat-login", {
    method: "POST",
    data: { code },
    auth: false
  }).then((response) => {
    saveSession(response);
    return response;
  });
}

export function loginWithWechat(): Promise<AuthResponse> {
  return new Promise((resolve, reject) => {
    wx.login({
      success: async (result) => {
        if (!result.code) {
          reject(new ApiError(401, "WECHAT_LOGIN_FAILED", "微信登录失败"));
          return;
        }
        try {
          resolve(await loginWithWechatCode(result.code));
        } catch (error) {
          reject(error);
        }
      },
      fail: (failure) => {
        reject(new ApiError(0, "WECHAT_LOGIN_FAILED", failure.errMsg || "微信登录失败"));
      }
    });
  });
}

export function fetchMe(): Promise<MeResponse> {
  return request<MeResponse>("/api/v1/me");
}

export async function refreshSession(): Promise<Session | null> {
  const current = getSession();
  if (!current) {
    return null;
  }
  try {
    const me = await fetchMe();
    const refreshed: Session = {
      ...current,
      userId: me.userId,
      nickname: me.nickname,
      role: me.role,
      managedTeamIds: me.managedTeamIds
    };
    saveSession(refreshed);
    return refreshed;
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      clearSession();
    }
    return null;
  }
}

export function logout(): void {
  clearSession();
}
