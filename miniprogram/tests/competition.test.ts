import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "../services/http";
import { fetchFixtureDetail, fetchPublicFixtures } from "../services/competition";

vi.mock("../services/http", () => ({
  request: vi.fn()
}));

const requestMock = vi.mocked(request);

describe("competition services", () => {
  beforeEach(() => requestMock.mockReset());

  it("requests public fixtures by season", async () => {
    requestMock.mockResolvedValueOnce([]);
    await fetchPublicFixtures(5);
    expect(requestMock).toHaveBeenCalledWith("/api/v1/fixtures?seasonId=5");
  });

  it("requests fixture detail", async () => {
    requestMock.mockResolvedValueOnce({ id: 9, events: [] });
    await fetchFixtureDetail(9);
    expect(requestMock).toHaveBeenCalledWith("/api/v1/fixtures/9");
  });

  it("preserves API errors", async () => {
    const error = new Error("not found");
    requestMock.mockRejectedValueOnce(error);
    await expect(fetchFixtureDetail(404)).rejects.toBe(error);
  });
});
