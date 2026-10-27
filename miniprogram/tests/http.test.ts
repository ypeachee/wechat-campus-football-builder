import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, request, setUnauthorizedHandler } from "../services/http";
import { clearSession, getSession, saveSession } from "../state/session";

describe("http request", () => {
  beforeEach(() => {
    clearSession();
    setUnauthorizedHandler(() => undefined);
  });

  it("attaches the bearer token", async () => {
    saveSession({
      token: "token-1",
      userId: 1,
      nickname: "Viewer",
      role: "VIEWER",
      managedTeamIds: []
    });
    const requestMock = vi.fn((options: WechatMiniprogram.RequestOption) => {
      options.success?.({ statusCode: 200, data: { ok: true }, header: {} } as unknown as WechatMiniprogram.RequestSuccessCallbackResult);
      return {} as WechatMiniprogram.RequestTask;
    });
    (globalThis as unknown as { wx: { request: typeof requestMock } }).wx.request = requestMock;

    const result = await request<{ ok: boolean }>("/api/v1/teams");

    expect(result.ok).toBe(true);
    expect(requestMock.mock.calls[0][0].header?.Authorization).toBe("Bearer token-1");
  });

  it("clears the session and notifies once on 401", async () => {
    saveSession({
      token: "expired",
      userId: 7,
      nickname: null,
      role: "VIEWER",
      managedTeamIds: []
    });
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    (globalThis as unknown as { wx: { request: unknown } }).wx.request = vi.fn(
      (options: WechatMiniprogram.RequestOption) => {
        options.success?.({
          statusCode: 401,
          data: { code: "UNAUTHORIZED", message: "expired", requestId: "rid-1" },
          header: {}
        } as unknown as WechatMiniprogram.RequestSuccessCallbackResult);
        return {} as WechatMiniprogram.RequestTask;
      }
    );

    await expect(request("/api/v1/me")).rejects.toMatchObject({
      status: 401,
      code: "UNAUTHORIZED",
      requestId: "rid-1"
    });
    expect(getSession()).toBeNull();
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });
});

