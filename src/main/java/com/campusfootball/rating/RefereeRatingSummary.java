package com.campusfootball.rating;

public record RefereeRatingSummary(String refereeName, double averageScore, int ratingCount,
                                   int star1Count, int star2Count, int star3Count,
                                   int star4Count, int star5Count) {
}
