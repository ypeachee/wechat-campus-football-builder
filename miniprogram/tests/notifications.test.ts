import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "../services/http";
import { fetchDisciplineCenter } from "../services/discipline";
import { fetchNotifications, markAllNotificationsRead, markNotificationRead } from "../services/notifications";

vi.mock("../services/http", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../services/http")>();
  return { ...actual, request: vi.fn() };
});
const requestMock = vi.mocked(request);

describe("notification and discipline services", () => {
  beforeEach(() => requestMock.mockReset());

  it("uses user-isolated notification endpoints", async () => {
    requestMock.mockResolvedValue({ items: [], unreadCount: 0, page: 0, size: 30 });
    await fetchNotifications(1, 20);
    requestMock.mockResolvedValue(undefined);
    await markNotificationRead(9);
    await markAllNotificationsRead();
    expect(requestMock).toHaveBeenNthCalledWith(1, "/api/v1/notifications?page=1&size=20");
    expect(requestMock).toHaveBeenNthCalledWith(2, "/api/v1/notifications/9/read", { method: "PUT" });
    expect(requestMock).toHaveBeenNthCalledWith(3, "/api/v1/notifications/read-all", { method: "PUT" });
  });

  it("filters discipline records by season and team", async () => {
    requestMock.mockResolvedValue({ suspensions: [], cards: [] });
    await fetchDisciplineCenter(8, 12);
    expect(requestMock).toHaveBeenCalledWith("/api/v1/manage/discipline?seasonId=8&teamId=12");
  });
});