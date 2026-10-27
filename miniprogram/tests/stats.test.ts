import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "../services/http";
import { fetchAppearances, fetchAssists, fetchScorers, fetchStandings } from "../services/stats";

vi.mock("../services/http", () => ({ request: vi.fn() }));
const requestMock = vi.mocked(request);

describe("stats services", () => {
  beforeEach(() => requestMock.mockReset());

  it("requests all ranking endpoints with seasonId", async () => {
    requestMock.mockResolvedValue([]);
    await fetchStandings(5);
    await fetchScorers(5);
    await fetchAssists(5);
    await fetchAppearances(5);
    expect(requestMock).toHaveBeenNthCalledWith(1, "/api/v1/standings?seasonId=5");
    expect(requestMock).toHaveBeenNthCalledWith(2, "/api/v1/stats/scorers?seasonId=5");
    expect(requestMock).toHaveBeenNthCalledWith(3, "/api/v1/stats/assists?seasonId=5");
    expect(requestMock).toHaveBeenNthCalledWith(4, "/api/v1/stats/appearances?seasonId=5");
  });

  it("preserves API order", async () => {
    requestMock.mockResolvedValueOnce([
      { playerId: 1, playerName: "A", teamId: 1, teamName: "T1", goals: 3 },
      { playerId: 2, playerName: "B", teamId: 1, teamName: "T1", goals: 2 }
    ]);
    const rows = await fetchScorers(1);
    expect(rows.map((row) => row.playerId)).toEqual([1, 2]);
  });
});
