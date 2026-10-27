package com.campusfootball.rating;

import java.util.List;

public record RatingCommentPage(double averageScore, int ratingCount, int page, int size,
                                long total, List<RatingCommentView> comments) {
}
