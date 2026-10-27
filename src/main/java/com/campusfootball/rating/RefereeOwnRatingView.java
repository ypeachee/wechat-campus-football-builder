package com.campusfootball.rating;

import java.time.LocalDateTime;

public record RefereeOwnRatingView(boolean rated, Long id, Integer stars, Integer score,
                                   boolean canEdit, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static RefereeOwnRatingView none() {
        return new RefereeOwnRatingView(false, null, null, null, false, null, null);
    }
}
