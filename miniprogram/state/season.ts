import type { Season } from "../types/domain";

const SEASON_KEY = "campus_football_selected_season";

export function getSelectedSeasonId(): number | null {
  const value = wx.getStorageSync(SEASON_KEY);
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

export function setSelectedSeasonId(seasonId: number | null): void {
  if (seasonId === null) {
    wx.removeStorageSync(SEASON_KEY);
    return;
  }
  wx.setStorageSync(SEASON_KEY, seasonId);
}

export function selectDefaultSeason(seasons: Season[]): number | null {
  if (seasons.length === 0) {
    setSelectedSeasonId(null);
    return null;
  }
  const current = getSelectedSeasonId();
  if (current !== null && seasons.some((season) => season.id === current)) {
    return current;
  }
  const selected = seasons.find((season) => season.status === "ACTIVE") ?? seasons[0];
  setSelectedSeasonId(selected.id);
  return selected.id;
}
