package com.campusfootball.rating;

public record PlayerRatingSummary(long playerId, String playerName, String jerseyNo,
                                  long teamId, String teamName, double averageScore,
                                  int ratingCount, int star1Count, int star2Count,
                                  int star3Count, int star4Count, int star5Count) {
}
