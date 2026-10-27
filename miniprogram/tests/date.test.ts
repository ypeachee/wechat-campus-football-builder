import { describe, expect, it } from "vitest";
import { formatDate, formatMatchTime } from "../utils/date";

describe("date helpers", () => {
  it("formats match time for Chinese UI", () => {
    expect(formatMatchTime("2026-10-27T19:30:00")).toBe("10月27日 19:30");
  });

  it("formats date-only values", () => {
    expect(formatDate("2026-10-27")).toBe("2026-10-27");
  });

  it("returns a fallback for missing values", () => {
    expect(formatMatchTime(null)).toBe("时间待定");
    expect(formatDate(null)).toBe("未设置");
  });
});
