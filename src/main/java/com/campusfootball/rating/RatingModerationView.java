package com.campusfootball.rating;

import java.time.LocalDateTime;

public record RatingModerationView(long id, long fixtureId, long playerId, String playerName,
                                   String teamName, String nickname, int stars, String comment,
                                   RatingStatus status, String moderationReason, int reporterCount,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
}
