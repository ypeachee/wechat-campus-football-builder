import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "../services/http";
import {
  createSensitiveTerm, fetchFixtureRatings, fetchModerationRatings, fetchMyRating,
  fetchPlayerComments, importSensitiveTerms, moderateRating, reportRating, submitPlayerRating
} from "../services/ratings";

vi.mock("../services/http", () => ({ request: vi.fn() }));
const requestMock = vi.mocked(request);

describe("rating services", () => {
  beforeEach(() => requestMock.mockReset());
  it("requests public fixture and player ratings", async () => {
    requestMock.mockResolvedValueOnce([]).mockResolvedValueOnce({ comments: [] });
    await fetchFixtureRatings(9);
    await fetchPlayerComments(9, 3, 2, 20);
    expect(requestMock).toHaveBeenNthCalledWith(1, "/api/v1/fixtures/9/player-ratings");
    expect(requestMock).toHaveBeenNthCalledWith(2, "/api/v1/fixtures/9/players/3/ratings?page=2&size=20");
  });
  it("submits five-star rating and personal lookup", async () => {
    requestMock.mockResolvedValueOnce({ rated: false }).mockResolvedValueOnce({ id: 5 });
    await fetchMyRating(9, 3);
    await submitPlayerRating(9, 3, 4, "  防守稳健  ");
    expect(requestMock).toHaveBeenNthCalledWith(1, "/api/v1/fixtures/9/players/3/rating/me");
    expect(requestMock).toHaveBeenNthCalledWith(2, "/api/v1/fixtures/9/players/3/rating", {
      method: "PUT", data: { stars: 4, comment: "防守稳健" }
    });
  });
  it("reports and moderates ratings", async () => {
    requestMock.mockResolvedValue(undefined);
    await reportRating(12, " 不当内容 ");
    await moderateRating(12, "HIDDEN", "下线");
    expect(requestMock).toHaveBeenNthCalledWith(1, "/api/v1/ratings/12/report", {
      method: "POST", data: { reason: "不当内容" }
    });
    expect(requestMock).toHaveBeenNthCalledWith(2, "/api/v1/admin/ratings/12/status", {
      method: "PUT", data: { status: "HIDDEN", reason: "下线" }
    });
  });
  it("manages moderation lists and sensitive terms", async () => {
    requestMock.mockResolvedValueOnce({ total: 0, items: [] }).mockResolvedValueOnce({ id: 1 })
      .mockResolvedValueOnce({ inserted: 2 });
    await fetchModerationRatings("PENDING", 1, 20);
    await createSensitiveTerm("禁发词", "广告", "BLOCK");
    await importSensitiveTerms(["词一", "词二"], "REVIEW");
    expect(requestMock).toHaveBeenNthCalledWith(1, "/api/v1/admin/ratings?status=PENDING&page=1&size=20");
    expect(requestMock).toHaveBeenNthCalledWith(2, "/api/v1/admin/sensitive-terms", {
      method: "POST", data: { term: "禁发词", category: "广告", action: "BLOCK" }
    });
    expect(requestMock).toHaveBeenNthCalledWith(3, "/api/v1/admin/sensitive-terms/import", {
      method: "POST", data: { terms: ["词一", "词二"], action: "REVIEW" }
    });
  });
});
