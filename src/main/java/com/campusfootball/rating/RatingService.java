package com.campusfootball.rating;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.competition.Fixture;
import com.campusfootball.competition.FixtureMapper;
import com.campusfootball.competition.FixtureStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class RatingService {
    private static final int MAX_COMMENT_LENGTH = 100;
    private static final int MAX_SUBMISSIONS_PER_WINDOW = 20;
    private static final Duration SUBMISSION_WINDOW = Duration.ofMinutes(10);
    private static final Duration EDIT_WINDOW = Duration.ofHours(24);

    private final RatingMapper ratingMapper;
    private final FixtureMapper fixtureMapper;
    private final ContentModerationService contentModerationService;
    private final AuditService auditService;
    private final Clock clock;

    public RatingService(RatingMapper ratingMapper, FixtureMapper fixtureMapper,
                         ContentModerationService contentModerationService,
                         AuditService auditService, Clock clock) {
        this.ratingMapper = ratingMapper;
        this.fixtureMapper = fixtureMapper;
        this.contentModerationService = contentModerationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public RatingSubmissionResult submit(long userId, long fixtureId, long playerId, int stars, String comment) {
        if (stars < 1 || stars > 5) {
            throw badRequest("评分必须为 1–5 星");
        }
        String cleanedComment = normalizeComment(comment);
        Fixture fixture = fixtureMapper.findById(fixtureId).orElseThrow(() -> notFound("比赛不存在"));
        if (fixture.getStatus() != FixtureStatus.FINISHED) {
            throw conflict("比赛结束后才能评分");
        }
        long teamId = ratingMapper.findAppearanceTeamId(fixtureId, playerId)
                .orElseThrow(() -> conflict("该球员没有出场记录，不能评分"));

        Optional<PlayerMatchRating> existing = ratingMapper.findByTarget(fixtureId, playerId, userId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (existing.isPresent()) {
            PlayerMatchRating rating = existing.get();
            if (rating.getEditsUsed() != null && rating.getEditsUsed() >= 1) {
                throw conflict("评分已修改过，不能再次修改");
            }
            if (rating.getUpdatedAt() == null || rating.getUpdatedAt().plus(EDIT_WINDOW).isBefore(now)) {
                throw conflict("评分修改时间已超过 24 小时");
            }
            applyModeratedContent(rating, userId, cleanedComment);
            rating.setStars(stars);
            rating.setTeamId(teamId);
            rating.setEditsUsed(1);
            rating.setUpdatedAt(now);
            ratingMapper.updateSubmission(rating);
            auditService.record(userId, teamId, AuditAction.UPDATE, "PLAYER_RATING", rating.getId(), null, rating);
            return result(rating, false);
        }

        LocalDateTime windowStart = now.minus(SUBMISSION_WINDOW);
        if (ratingMapper.countRecentSubmissions(userId, windowStart) >= MAX_SUBMISSIONS_PER_WINDOW) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED,
                    "评分提交过于频繁，请稍后再试");
        }
        PlayerMatchRating rating = new PlayerMatchRating();
        rating.setFixtureId(fixtureId);
        rating.setPlayerId(playerId);
        rating.setTeamId(teamId);
        rating.setUserId(userId);
        rating.setStars(stars);
        rating.setEditsUsed(0);
        rating.setCreatedAt(now);
        rating.setUpdatedAt(now);
        applyModeratedContent(rating, userId, cleanedComment);
        ratingMapper.insert(rating);
        auditService.record(userId, teamId, AuditAction.CREATE, "PLAYER_RATING", rating.getId(), null, rating);
        return result(rating, true);
    }

    public Optional<PlayerMatchRating> findMine(long userId, long fixtureId, long playerId) {
        return ratingMapper.findByTarget(fixtureId, playerId, userId);
    }

    public boolean canEdit(PlayerMatchRating rating) {
        return (rating.getEditsUsed() == null || rating.getEditsUsed() < 1)
                && rating.getUpdatedAt() != null
                && rating.getUpdatedAt().plus(EDIT_WINDOW).isAfter(LocalDateTime.now(clock));
    }

    @Transactional
    public void report(long userId, long ratingId, String reason) {
        PlayerMatchRating rating = ratingMapper.findById(ratingId).orElseThrow(() -> notFound("评分不存在"));
        if (rating.getUserId() == userId) {
            throw conflict("不能举报自己的评价");
        }
        if (rating.getStatus() != RatingStatus.VISIBLE || rating.getComment() == null) {
            throw conflict("该评价当前不可举报");
        }
        String cleanedReason = reason == null ? "" : reason.trim();
        if (cleanedReason.isEmpty() || cleanedReason.length() > 100) {
            throw badRequest("举报原因长度必须为 1–100 个字符");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (ratingMapper.countRecentReports(userId, now.minus(SUBMISSION_WINDOW)) >= 10) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED,
                    "举报过于频繁，请稍后再试");
        }
        RatingReport report = new RatingReport();
        report.setRatingId(ratingId);
        report.setReporterUserId(userId);
        report.setReason(cleanedReason);
        report.setStatus(RatingReportStatus.OPEN);
        report.setCreatedAt(now);
        try {
            ratingMapper.insertReport(report);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw conflict("你已举报过该评价");
        }
    }

    private void applyModeratedContent(PlayerMatchRating rating, long userId, String comment) {
        rating.setComment(comment);
        if (comment == null) {
            rating.setStatus(RatingStatus.VISIBLE);
            rating.setModerationReason(null);
            return;
        }
        ModerationResult moderation = contentModerationService.evaluate(userId, comment);
        rating.setStatus(switch (moderation.decision()) {
            case PASS -> RatingStatus.VISIBLE;
            case REVIEW -> RatingStatus.PENDING;
            case BLOCK -> RatingStatus.REJECTED;
        });
        rating.setModerationReason(moderation.reason());
    }

    private RatingSubmissionResult result(PlayerMatchRating rating, boolean canEdit) {
        return new RatingSubmissionResult(rating.getId(), rating.getStars(), rating.getStars() * 2,
                rating.getStatus(), rating.getModerationReason(), canEdit);
    }

    private String normalizeComment(String comment) {
        String cleaned = comment == null ? "" : comment.trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > MAX_COMMENT_LENGTH) {
            throw badRequest("短评不能超过 100 个字符");
        }
        return cleaned;
    }

    private AppException badRequest(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }

    private AppException conflict(String message) {
        return new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }

    private AppException notFound(String message) {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }
}
