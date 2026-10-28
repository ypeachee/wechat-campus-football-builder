import { beforeEach, describe, expect, it, vi } from "vitest";
import { clearSession, getSession, requireRole, saveSession } from "../state/session";
import { refreshSession } from "../services/auth";

describe("role gates and session refresh", () => {
  beforeEach(() => clearSession());

  it("rejects unauthenticated users", () => {
    expect(requireRole(["ADMIN", "TEAM_MANAGER"])).toBe(false);
  });

  it("rejects viewers from management", () => {
    saveSession({
      token: "token",
      userId: 1,
      nickname: null,
      role: "VIEWER",
      managedTeamIds: []
    });
    expect(requireRole(["ADMIN", "TEAM_MANAGER"])).toBe(false);
  });

  it("allows authorized managers on every team gate", () => {
    saveSession({
      token: "token",
      userId: 2,
      nickname: null,
      role: "TEAM_MANAGER",
      managedTeamIds: [3]
    });
    expect(requireRole(["TEAM_MANAGER"], 3)).toBe(true);
    expect(requireRole(["TEAM_MANAGER"], 4)).toBe(true);
  });

  it("scopes team leaders to assigned teams", () => {
    saveSession({
      token: "leader",
      userId: 5,
      nickname: null,
      role: "TEAM_LEADER",
      managedTeamIds: [3]
    });
    expect(requireRole(["TEAM_LEADER"], 3)).toBe(true);
    expect(requireRole(["TEAM_LEADER"], 4)).toBe(false);
  });

  it("allows admins on every team gate", () => {
    saveSession({
      token: "token",
      userId: 3,
      nickname: null,
      role: "ADMIN",
      managedTeamIds: []
    });
    expect(requireRole(["ADMIN"], 99)).toBe(true);
  });

  it("replaces a stale local role from /me", async () => {
    saveSession({
      token: "token",
      userId: 4,
      nickname: null,
      role: "VIEWER",
      managedTeamIds: []
    });
    (globalThis as unknown as { wx: { request: unknown } }).wx.request = vi.fn(
      (options: WechatMiniprogram.RequestOption) => {
        options.success?.({
          statusCode: 200,
          data: { userId: 4, nickname: "Manager", role: "TEAM_MANAGER", managedTeamIds: [7] },
          header: {}
        } as unknown as WechatMiniprogram.RequestSuccessCallbackResult);
        return {} as WechatMiniprogram.RequestTask;
      }
    );

    const refreshed = await refreshSession();
    expect(refreshed?.role).toBe("TEAM_MANAGER");
    expect(getSession()?.managedTeamIds).toEqual([7]);
  });

  it("clears the session when /me returns 401", async () => {
    saveSession({
      token: "expired",
      userId: 5,
      nickname: null,
      role: "VIEWER",
      managedTeamIds: []
    });
    (globalThis as unknown as { wx: { request: unknown } }).wx.request = vi.fn(
      (options: WechatMiniprogram.RequestOption) => {
        options.success?.({
          statusCode: 401,
          data: { code: "UNAUTHORIZED", message: "expired", requestId: "rid" },
          header: {}
        } as unknown as WechatMiniprogram.RequestSuccessCallbackResult);
        return {} as WechatMiniprogram.RequestTask;
      }
    );

    await expect(refreshSession()).resolves.toBeNull();
    expect(getSession()).toBeNull();
  });
});
