import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "../services/http";
import { createAnnouncement, fetchAnnouncements } from "../services/announcements";
import { fetchManagementDashboard } from "../services/dashboard";
import { uploadImportFile } from "../services/imports";
import { saveSession } from "../state/session";

vi.mock("../services/http", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../services/http")>();
  return { ...actual, request: vi.fn() };
});
const requestMock = vi.mocked(request);

describe("announcement, dashboard and import services", () => {
  beforeEach(() => requestMock.mockReset());

  it("uses announcement and dashboard endpoints", async () => {
    requestMock.mockResolvedValue({ items: [], total: 0, page: 0, size: 20 });
    await fetchAnnouncements();
    requestMock.mockResolvedValue({ pendingRatings: 1, reportedRatings: 2, tomorrowFixtures: 3, activeSuspensions: 4 });
    await fetchManagementDashboard();
    requestMock.mockResolvedValue({ id: 1 });
    await createAnnouncement("标题", "正文", "ALL");
    expect(requestMock).toHaveBeenNthCalledWith(1, "/api/v1/admin/announcements?page=0&size=20");
    expect(requestMock).toHaveBeenNthCalledWith(2, "/api/v1/manage/dashboard");
    expect(requestMock).toHaveBeenNthCalledWith(3, "/api/v1/admin/announcements", {
      method: "POST", data: { title: "标题", content: "正文", audience: "ALL", targetTeamId: undefined, targetUserId: undefined }
    });
  });

  it("uploads import preview with bearer token", async () => {
    saveSession({ token: "token-1", userId: 1, nickname: null, role: "ADMIN", managedTeamIds: [] });
    const upload = vi.fn((options: WechatMiniprogram.UploadFileOption) => {
      options.success?.({ statusCode: 200, data: "{\"totalRows\":1}", header: {}, errMsg: "uploadFile:ok", profile: {} } as unknown as WechatMiniprogram.UploadFileSuccessCallbackResult);
      return {} as WechatMiniprogram.UploadTask;
    });
    (globalThis as unknown as { wx: { uploadFile: typeof upload } }).wx.uploadFile = upload;

    await uploadImportFile("players", "preview", "wxfile://players.csv", true);
    expect(upload).toHaveBeenCalledWith(expect.objectContaining({
      filePath: "wxfile://players.csv",
      name: "file",
      formData: { overwrite: "true" },
      header: { Authorization: "Bearer token-1" }
    }));
  });
});