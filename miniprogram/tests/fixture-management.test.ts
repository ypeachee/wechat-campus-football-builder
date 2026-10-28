import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "../services/http";
import { ApiError } from "../services/http";
import { createFixture, finishFixture, setFixtureLocked } from "../services/competition";
import { createEvent, createSuspension, fetchFixtureSuspensions, fetchTeamSuspensions, replaceTeamLineup, updateSuspension } from "../services/manage";
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
    await createFixture({ seasonId: 1, stageId: 4, groupId: 5, homeTeamId: 2, awayTeamId: 3 });
    requestMock.mockResolvedValueOnce(undefined);
    await finishFixture(10, 2, 1);
    expect(requestMock).toHaveBeenNthCalledWith(1, "/api/v1/admin/fixtures", {
      method: "POST",
      data: { seasonId: 1, stageId: 4, groupId: 5, homeTeamId: 2, awayTeamId: 3 }
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

  it("saves a complete team lineup in one request", async () => {
    requestMock.mockResolvedValueOnce(undefined);
    await replaceTeamLineup(10, 2, [8, 9], [{ playerId: 10, minutesPlayed: 32 }]);
    expect(requestMock).toHaveBeenCalledWith("/api/v1/manage/fixtures/10/teams/2/appearances", {
      method: "PUT",
      data: {
        starters: [8, 9],
        substitutes: [{ playerId: 10, minutesPlayed: 32 }]
      }
    });
  });

  it("loads and updates player suspensions", async () => {
    requestMock.mockResolvedValueOnce([]);
    requestMock.mockResolvedValueOnce([]);
    requestMock.mockResolvedValueOnce({ id: 31 });
    requestMock.mockResolvedValueOnce(undefined);

    await fetchTeamSuspensions(2, 5);
    await fetchFixtureSuspensions(10);
    await createSuspension(8, 5, 2, "纪律处罚");
    await updateSuspension(31, 3, "ACTIVE");

    expect(requestMock).toHaveBeenNthCalledWith(1, "/api/v1/manage/teams/2/suspensions?seasonId=5");
    expect(requestMock).toHaveBeenNthCalledWith(2, "/api/v1/manage/fixtures/10/suspensions");
    expect(requestMock).toHaveBeenNthCalledWith(3, "/api/v1/manage/suspensions", {
      method: "POST",
      data: { playerId: 8, seasonId: 5, matchesTotal: 2, reason: "纪律处罚" }
    });
    expect(requestMock).toHaveBeenNthCalledWith(4, "/api/v1/manage/suspensions/31", {
      method: "PUT",
      data: { matchesTotal: 3, status: "ACTIVE" }
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
