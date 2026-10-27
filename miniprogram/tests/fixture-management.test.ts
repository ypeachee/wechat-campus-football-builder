import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "../services/http";
import { ApiError } from "../services/http";
import { createFixture, finishFixture, setFixtureLocked } from "../services/competition";
import { createEvent } from "../services/manage";
import { fetchAuditLogs } from "../services/audit";
import { requireRole, saveSession, clearSession } from "../state/session";
import { errorMessage } from "../utils/error";

vi.mock("../services/http", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../services/http")>();
  return { ...actual, request: vi.fn() };
});
const requestMock = vi.mocked(request);

describe("fixture management", () => {
  beforeEach(() => {
    requestMock.mockReset();
    clearSession();
  });

  it("creates fixtures and finishes with scores", async () => {
    requestMock.mockResolvedValue({ id: 10 });
    await createFixture({ seasonId: 1, homeTeamId: 2, awayTeamId: 3 });
    requestMock.mockResolvedValueOnce(undefined);
    await finishFixture(10, 2, 1);
    expect(requestMock).toHaveBeenNthCalledWith(1, "/api/v1/admin/fixtures", {
      method: "POST",
      data: { seasonId: 1, homeTeamId: 2, awayTeamId: 3 }
    });
    expect(requestMock).toHaveBeenNthCalledWith(2, "/api/v1/admin/fixtures/10/finish", {
      method: "POST",
      data: { homeScore: 2, awayScore: 1 }
    });
  });

  it("maps fixture lock errors to a clear message", () => {
    expect(errorMessage(new ApiError(403, "FIXTURE_LOCKED", "该场比赛的数据已锁定")))
      .toBe("比赛已锁定，请管理员先解锁");
  });

  it("sends nullable assist values for events", async () => {
    requestMock.mockResolvedValue({ id: 11 });
    await createEvent(10, {
      teamId: 2,
      playerId: 8,
      eventType: "GOAL",
      relatedPlayerId: null,
      minute: 12,
      stoppageMinute: 0
    });
    expect(requestMock).toHaveBeenCalledWith("/api/v1/manage/fixtures/10/events", {
      method: "POST",
      data: expect.objectContaining({ relatedPlayerId: null })
    });
  });

  it("locks fixtures and fetches audit logs", async () => {
    requestMock.mockResolvedValueOnce(undefined);
    requestMock.mockResolvedValueOnce([]);
    await setFixtureLocked(10, true);
    await fetchAuditLogs();
    expect(requestMock).toHaveBeenNthCalledWith(1, "/api/v1/admin/fixtures/10/lock", {
      method: "PUT",
      data: { locked: true }
    });
    expect(requestMock).toHaveBeenNthCalledWith(2, "/api/v1/manage/audit-logs");
  });

  it("does not expose admin controls to viewers", () => {
    saveSession({ token: "t", userId: 1, nickname: null, role: "VIEWER", managedTeamIds: [] });
    expect(requireRole(["ADMIN"])).toBe(false);
  });
});
