package com.campusfootball.rating;

public record PlayerSeasonRatingSummary(long playerId, Long seasonId, double averageScore, int ratingCount) {
}
