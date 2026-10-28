import { beforeEach, describe, expect, it } from "vitest";
import { canManageTeam, clearSession, getSession, isAdmin, saveSession } from "../state/session";

describe("session store", () => {
  beforeEach(() => clearSession());

  it("persists and clears users", () => {
    saveSession({
      token: "token",
      userId: 1,
      nickname: "Manager",
      role: "TEAM_MANAGER",
      managedTeamIds: [3]
    });

    expect(getSession()?.role).toBe("TEAM_MANAGER");
    clearSession();
    expect(getSession()).toBeNull();
  });

  it("allows authorized managers to manage every team", () => {
    saveSession({
      token: "token",
      userId: 2,
      nickname: null,
      role: "TEAM_MANAGER",
      managedTeamIds: [3]
    });
    expect(canManageTeam(3)).toBe(true);
    expect(canManageTeam(4)).toBe(true);
    expect(isAdmin()).toBe(false);
  });

  it("scopes team leaders to assigned teams", () => {
    saveSession({
      token: "leader",
      userId: 4,
      nickname: null,
      role: "TEAM_LEADER",
      managedTeamIds: [3]
    });
    expect(canManageTeam(3)).toBe(true);
    expect(canManageTeam(4)).toBe(false);
    expect(isAdmin()).toBe(false);
  });

  it("allows admins to manage every team", () => {
    saveSession({
      token: "token",
      userId: 3,
      nickname: null,
      role: "ADMIN",
      managedTeamIds: []
    });
    expect(canManageTeam(99)).toBe(true);
    expect(isAdmin()).toBe(true);
  });
});
