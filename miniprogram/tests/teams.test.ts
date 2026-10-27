import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "../services/http";
import { fetchPlayer, fetchTeam, fetchTeamPlayers, fetchTeams } from "../services/teams";

vi.mock("../services/http", () => ({
  request: vi.fn()
}));

const requestMock = vi.mocked(request);

describe("team services", () => {
  beforeEach(() => requestMock.mockReset());

  it("requests the public team list", async () => {
    requestMock.mockResolvedValueOnce([]);
    await fetchTeams();
    expect(requestMock).toHaveBeenCalledWith("/api/v1/teams");
  });

  it("requests team detail and players", async () => {
    requestMock.mockResolvedValue({ id: 12 });
    await fetchTeam(12);
    await fetchTeamPlayers(12);
    expect(requestMock).toHaveBeenNthCalledWith(1, "/api/v1/teams/12");
    expect(requestMock).toHaveBeenNthCalledWith(2, "/api/v1/teams/12/players");
  });

  it("preserves player API errors", async () => {
    const error = new Error("not found");
    requestMock.mockRejectedValueOnce(error);
    await expect(fetchPlayer(99)).rejects.toBe(error);
  });
});
