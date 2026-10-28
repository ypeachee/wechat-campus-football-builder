import { describe, expect, it } from "vitest";
import fs from "node:fs";
import path from "node:path";
import { ApiError } from "../services/http";
import { clearSession, requireRole, saveSession } from "../state/session";
import { getSelectedSeasonId, selectDefaultSeason, setSelectedSeasonId } from "../state/season";
import { SubmitLock, withSubmitLock } from "../state/submit-lock";
import { resolveSelectedId } from "../state/selection";
import { errorMessage } from "../utils/error";
import type { Season } from "../types/domain";

describe("mini program acceptance flows", () => {
  it("keeps public season selection consistent", () => {
    clearSession();
    setSelectedSeasonId(null);
    const seasons: Season[] = [
      { id: 1, name: "Draft", startDate: null, endDate: null, status: "DRAFT" },
      { id: 2, name: "Active", startDate: null, endDate: null, status: "ACTIVE" }
    ];
    expect(selectDefaultSeason(seasons)).toBe(2);
    expect(getSelectedSeasonId()).toBe(2);
  });

  it("binds the fixtures template to the list populated by the page", () => {
    const template = fs.readFileSync(path.resolve(__dirname, "../pages/fixtures/index.wxml"), "utf8");
    expect(template).toContain("wx:for=\"{{fixtureViews}}\"");
    expect(template).toContain("fixtureViews.length");
    expect(template).not.toContain("filteredFixtures");
  });

  it("drops stale competition hierarchy selections", () => {
    expect(resolveSelectedId([{ id: 10 }, { id: 11 }], 10)).toBe(10);
    expect(resolveSelectedId([{ id: 10 }, { id: 11 }], 99)).toBe(10);
    expect(resolveSelectedId([], 99)).toBe(0);
  });

  it("applies viewer, manager and admin visibility rules", () => {
    clearSession();
    expect(requireRole(["ADMIN", "TEAM_MANAGER"])).toBe(false);

    saveSession({ token: "v", userId: 1, nickname: null, role: "VIEWER", managedTeamIds: [] });
    expect(requireRole(["ADMIN", "TEAM_MANAGER"])).toBe(false);

    saveSession({ token: "m", userId: 2, nickname: null, role: "TEAM_MANAGER", managedTeamIds: [7] });
    expect(requireRole(["TEAM_MANAGER"], 7)).toBe(true);
    expect(requireRole(["TEAM_MANAGER"], 8)).toBe(true);

    saveSession({ token: "l", userId: 4, nickname: null, role: "TEAM_LEADER", managedTeamIds: [7] });
    expect(requireRole(["TEAM_LEADER"], 7)).toBe(true);
    expect(requireRole(["TEAM_LEADER"], 8)).toBe(false);

    saveSession({ token: "a", userId: 3, nickname: null, role: "ADMIN", managedTeamIds: [] });
    expect(requireRole(["ADMIN"], 99)).toBe(true);
  });

  it("turns locked-fixture failures into clear copy and releases submit locks", async () => {
    expect(errorMessage(new ApiError(403, "FIXTURE_LOCKED", "locked")))
      .toBe("比赛已锁定，请管理员先解锁");

    const lock = new SubmitLock();
    await expect(withSubmitLock(lock, async () => {
      throw new Error("submission failed");
    })).rejects.toThrow("submission failed");
    expect(lock.tryAcquire()).toBe(true);
  });
});
