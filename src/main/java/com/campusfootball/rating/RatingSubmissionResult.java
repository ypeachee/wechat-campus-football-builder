package com.campusfootball.rating;

public record RatingSubmissionResult(long id, int stars, int score, RatingStatus status,
                                     String moderationReason, boolean canEdit) {
}
