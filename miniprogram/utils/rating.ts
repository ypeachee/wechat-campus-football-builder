import type { FixtureStatus, RatingStatus } from "../types/domain";

export function starsToScore(stars: number): number {
  if (!Number.isInteger(stars) || stars < 1 || stars > 5) {
    throw new RangeError("stars must be an integer from 1 to 5");
  }
  return stars * 2;
}

export function formatRatingScore(score: number | null | undefined): string {
  return score === null || score === undefined || !Number.isFinite(score) ? "-" : `${score.toFixed(1)}/10`;
}

export function starLabel(stars: number): string {
  const filled = Math.max(0, Math.min(5, Math.round(stars || 0)));
  return "★".repeat(filled) + "☆".repeat(5 - filled);
}

export function ratingStatusText(status: RatingStatus | null): string {
  switch (status) {
    case "PENDING": return "评价审核中，通过后公开展示";
    case "REJECTED": return "评价未通过审核";
    case "HIDDEN": return "评价已隐藏";
    case "VISIBLE": return "评价已公开";
    default: return "";
  }
}

export function canRateFixture(status: FixtureStatus): boolean {
  return status === "FINISHED";
}
