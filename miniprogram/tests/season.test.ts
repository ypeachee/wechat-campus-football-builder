import { beforeEach, describe, expect, it } from "vitest";
import { getSelectedSeasonId, selectDefaultSeason, setSelectedSeasonId } from "../state/season";
import type { Season } from "../types/domain";

describe("season store", () => {
  beforeEach(() => setSelectedSeasonId(null));

  it("persists the selected season", () => {
    setSelectedSeasonId(12);
    expect(getSelectedSeasonId()).toBe(12);
  });

  it("defaults to the first active season", () => {
    const seasons: Season[] = [
      { id: 1, name: "Draft", startDate: null, endDate: null, status: "DRAFT" },
      { id: 2, name: "Active", startDate: null, endDate: null, status: "ACTIVE" }
    ];
    expect(selectDefaultSeason(seasons)).toBe(2);
    expect(getSelectedSeasonId()).toBe(2);
  });

  it("keeps an existing valid selection", () => {
    setSelectedSeasonId(1);
    expect(selectDefaultSeason([
      { id: 1, name: "One", startDate: null, endDate: null, status: "FINISHED" },
      { id: 2, name: "Two", startDate: null, endDate: null, status: "ACTIVE" }
    ])).toBe(1);
  });
});
