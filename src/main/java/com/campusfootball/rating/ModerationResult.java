package com.campusfootball.rating;

public record ModerationResult(ModerationDecision decision, String matchedTerm, String reason) {
    public static ModerationResult pass() {
        return new ModerationResult(ModerationDecision.PASS, null, null);
    }

    public static ModerationResult review(String matchedTerm) {
        return new ModerationResult(ModerationDecision.REVIEW, matchedTerm, "内容需要人工审核");
    }

    public static ModerationResult block(String matchedTerm) {
        return new ModerationResult(ModerationDecision.BLOCK, matchedTerm, "内容包含违规词");
    }
}
