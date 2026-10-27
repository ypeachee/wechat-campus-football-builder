import { request } from "./http";
import type {
  OwnRating,
  PlayerRatingSummary,
  PlayerSeasonRatingSummary,
  RatingCommentPage,
  RatingModerationPage,
  RatingStatus,
  RatingSubmission,
  SensitiveTerm,
  SensitiveTermAction
} from "../types/domain";

export function fetchFixtureRatings(fixtureId: number): Promise<PlayerRatingSummary[]> {
  return request(`/api/v1/fixtures/${fixtureId}/player-ratings`);
}

export function fetchPlayerComments(fixtureId: number, playerId: number,
                                    page = 0, size = 20): Promise<RatingCommentPage> {
  return request(`/api/v1/fixtures/${fixtureId}/players/${playerId}/ratings?page=${page}&size=${size}`);
}

export function fetchPlayerRatingSummary(playerId: number,
                                         seasonId?: number | null): Promise<PlayerSeasonRatingSummary> {
  const season = seasonId ? `?seasonId=${seasonId}` : "";
  return request(`/api/v1/players/${playerId}/rating-summary${season}`);
}

export function fetchMyRating(fixtureId: number, playerId: number): Promise<OwnRating> {
  return request(`/api/v1/fixtures/${fixtureId}/players/${playerId}/rating/me`);
}

export function submitPlayerRating(fixtureId: number, playerId: number,
                                   stars: number, comment: string): Promise<RatingSubmission> {
  return request(`/api/v1/fixtures/${fixtureId}/players/${playerId}/rating`, {
    method: "PUT",
    data: { stars, comment: comment.trim() || null }
  });
}

export function reportRating(ratingId: number, reason: string): Promise<void> {
  return request(`/api/v1/ratings/${ratingId}/report`, {
    method: "POST",
    data: { reason: reason.trim() }
  });
}

export function fetchModerationRatings(status: string, page = 0, size = 20): Promise<RatingModerationPage> {
  return request(`/api/v1/admin/ratings?status=${encodeURIComponent(status)}&page=${page}&size=${size}`);
}

export function moderateRating(ratingId: number, status: RatingStatus, reason: string): Promise<void> {
  return request(`/api/v1/admin/ratings/${ratingId}/status`, {
    method: "PUT",
    data: { status, reason: reason.trim() || null }
  });
}

export function fetchSensitiveTerms(): Promise<SensitiveTerm[]> {
  return request("/api/v1/admin/sensitive-terms");
}

export function createSensitiveTerm(term: string, category: string,
                                    action: SensitiveTermAction): Promise<{ id: number }> {
  return request("/api/v1/admin/sensitive-terms", {
    method: "POST",
    data: { term: term.trim(), category: category.trim(), action }
  });
}

export function updateSensitiveTerm(id: number, category: string,
                                    action: SensitiveTermAction, enabled: boolean): Promise<void> {
  return request(`/api/v1/admin/sensitive-terms/${id}`, {
    method: "PUT",
    data: { category: category.trim(), action, enabled }
  });
}

export function deleteSensitiveTerm(id: number): Promise<void> {
  return request(`/api/v1/admin/sensitive-terms/${id}`, { method: "DELETE" });
}

export function importSensitiveTerms(terms: string[], action: SensitiveTermAction): Promise<{ inserted: number }> {
  return request("/api/v1/admin/sensitive-terms/import", {
    method: "POST",
    data: { terms, action }
  });
}
