import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "../services/http";
import {
  addSeasonTeam,
  createSeason,
  fetchAdminSeasons,
  fetchSeasonTeams,
  removeSeasonTeam
} from "../services/competition";
import { fetchTeamManagers, grantTeamManager, revokeTeamManager } from "../services/manage";

vi.mock("../services/http", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../services/http")>();
  return { ...actual, request: vi.fn() };
});
const requestMock = vi.mocked(request);

describe("admin services", () => {
  beforeEach(() => requestMock.mockReset());

  it("uses admin season endpoints", async () => {
    requestMock.mockResolvedValue([]);
    await fetchAdminSeasons();
    await fetchSeasonTeams(7);
    await addSeasonTeam(7, 3);
    await removeSeasonTeam(7, 3);
    expect(requestMock).toHaveBeenNthCalledWith(1, "/api/v1/admin/seasons");
    expect(requestMock).toHaveBeenNthCalledWith(2, "/api/v1/admin/seasons/7/teams");
    expect(requestMock).toHaveBeenNthCalledWith(3, "/api/v1/admin/seasons/7/teams/3", { method: "POST" });
    expect(requestMock).toHaveBeenNthCalledWith(4, "/api/v1/admin/seasons/7/teams/3", { method: "DELETE" });
  });

  it("does not create a season with an empty name", async () => {
    await expect(createSeason({ name: "  ", status: "DRAFT" })).rejects.toThrow("请输入赛季名称");
    expect(requestMock).not.toHaveBeenCalled();
  });

  it("uses manager grant endpoints", async () => {
    requestMock.mockResolvedValue([]);
    await fetchTeamManagers(7);
    await grantTeamManager(7, 21, "TEAM_LEADER");
    await revokeTeamManager(7, 21);
    expect(requestMock).toHaveBeenNthCalledWith(1, "/api/v1/admin/teams/7/managers");
    expect(requestMock).toHaveBeenNthCalledWith(2, "/api/v1/admin/teams/7/managers", { method: "POST", data: { userId: 21, role: "TEAM_LEADER" } });
    expect(requestMock).toHaveBeenNthCalledWith(3, "/api/v1/admin/teams/7/managers/21", { method: "DELETE" });
  });

  it("preserves conflicts", async () => {
    const conflict = new Error("conflict");
    requestMock.mockRejectedValueOnce(conflict);
    await expect(grantTeamManager(7, 21, "TEAM_MANAGER")).rejects.toBe(conflict);
  });
});
