import { describe, expect, it } from "vitest";
import { canRateFixture, formatRatingScore, ratingStatusText, starLabel, starsToScore } from "../utils/rating";

describe("rating helpers", () => {
  it("maps five stars to a ten point scale", () => {
    expect([1, 2, 3, 4, 5].map(starsToScore)).toEqual([2, 4, 6, 8, 10]);
    expect(() => starsToScore(0)).toThrow(RangeError);
    expect(() => starsToScore(6)).toThrow(RangeError);
    expect(() => starsToScore(2.5)).toThrow(RangeError);
  });
  it("formats scores and star labels", () => {
    expect(formatRatingScore(null)).toBe("-");
    expect(formatRatingScore(8.44)).toBe("8.4/10");
    expect(starLabel(4)).toBe("★★★★☆");
  });
  it("shows clear moderation and fixture states", () => {
    expect(ratingStatusText("PENDING")).toContain("审核中");
    expect(ratingStatusText("REJECTED")).toContain("未通过");
    expect(canRateFixture("FINISHED")).toBe(true);
    expect(canRateFixture("LIVE")).toBe(false);
  });
});
