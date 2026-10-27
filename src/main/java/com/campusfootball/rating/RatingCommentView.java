package com.campusfootball.rating;

import java.time.LocalDateTime;

public record RatingCommentView(long id, String nickname, int stars, int score,
                                String comment, LocalDateTime createdAt) {
}
