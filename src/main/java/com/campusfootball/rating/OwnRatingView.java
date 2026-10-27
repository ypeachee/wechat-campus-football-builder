package com.campusfootball.rating;

import java.time.LocalDateTime;

public record OwnRatingView(boolean rated, Long id, Integer stars, Integer score,
                            String comment, RatingStatus status, String moderationReason,
                            boolean canEdit, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static OwnRatingView none() {
        return new OwnRatingView(false, null, null, null, null, null, null, false, null, null);
    }
}
