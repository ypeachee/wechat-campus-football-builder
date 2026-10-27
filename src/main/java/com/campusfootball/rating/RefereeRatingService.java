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
public class RefereeRatingService {
    private static final int MAX_SUBMISSIONS_PER_WINDOW = 20;
    private static final Duration SUBMISSION_WINDOW = Duration.ofMinutes(10);
    private static final Duration EDIT_WINDOW = Duration.ofHours(24);

    private final RefereeRatingMapper mapper;
    private final FixtureMapper fixtureMapper;
    private final AuditService auditService;
    private final Clock clock;

    public RefereeRatingService(RefereeRatingMapper mapper, FixtureMapper fixtureMapper,
                                AuditService auditService, Clock clock) {
        this.mapper = mapper;
        this.fixtureMapper = fixtureMapper;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public RefereeRatingSubmission submit(long userId, long fixtureId, int stars) {
        if (stars < 1 || stars > 5) {
            throw badRequest("评分必须为 1–5 星");
        }
        Fixture fixture = requireRateableFixture(fixtureId);
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<FixtureRefereeRating> existing = mapper.findByTarget(fixtureId, fixture.getReferee(), userId);
        if (existing.isPresent()) {
            FixtureRefereeRating rating = existing.get();
            if (rating.getEditsUsed() != null && rating.getEditsUsed() >= 1) {
                throw conflict("主裁评分已修改过，不能再次修改");
            }
            if (rating.getUpdatedAt() == null || rating.getUpdatedAt().plus(EDIT_WINDOW).isBefore(now)) {
                throw conflict("主裁评分修改时间已超过 24 小时");
            }
            rating.setStars(stars);
            rating.setEditsUsed(1);
            rating.setUpdatedAt(now);
            mapper.updateSubmission(rating);
            auditService.record(userId, null, AuditAction.UPDATE, "REFEREE_RATING", rating.getId(), null, rating);
            return new RefereeRatingSubmission(rating.getId(), stars, stars * 2, false);
        }
        if (mapper.countRecentSubmissions(userId, now.minus(SUBMISSION_WINDOW)) >= MAX_SUBMISSIONS_PER_WINDOW) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED,
                    "主裁评分提交过于频繁，请稍后再试");
        }
        FixtureRefereeRating rating = new FixtureRefereeRating();
        rating.setFixtureId(fixtureId);
        rating.setRefereeName(fixture.getReferee());
        rating.setUserId(userId);
        rating.setStars(stars);
        rating.setEditsUsed(0);
        rating.setCreatedAt(now);
        rating.setUpdatedAt(now);
        mapper.insert(rating);
        auditService.record(userId, null, AuditAction.CREATE, "REFEREE_RATING", rating.getId(), null, rating);
        return new RefereeRatingSubmission(rating.getId(), stars, stars * 2, true);
    }

    public Optional<FixtureRefereeRating> findMine(long userId, long fixtureId) {
        Fixture fixture = fixtureMapper.findById(fixtureId).orElseThrow(() -> notFound("比赛不存在"));
        if (fixture.getReferee() == null || fixture.getReferee().isBlank()) {
            return Optional.empty();
        }
        return mapper.findByTarget(fixtureId, fixture.getReferee(), userId);
    }

    public RefereeRatingSummary summary(long fixtureId) {
        Fixture fixture = fixtureMapper.findById(fixtureId).orElseThrow(() -> notFound("比赛不存在"));
        if (fixture.getReferee() == null || fixture.getReferee().isBlank()) {
            return new RefereeRatingSummary(null, 0, 0, 0, 0, 0, 0, 0);
        }
        return rounded(mapper.summary(fixtureId, fixture.getReferee()));
    }

    public boolean canEdit(FixtureRefereeRating rating) {
        return (rating.getEditsUsed() == null || rating.getEditsUsed() < 1)
                && rating.getUpdatedAt() != null
                && rating.getUpdatedAt().plus(EDIT_WINDOW).isAfter(LocalDateTime.now(clock));
    }

    private Fixture requireRateableFixture(long fixtureId) {
        Fixture fixture = fixtureMapper.findById(fixtureId).orElseThrow(() -> notFound("比赛不存在"));
        if (fixture.getStatus() != FixtureStatus.FINISHED) {
            throw conflict("比赛结束后才能给主裁评分");
        }
        if (fixture.getReferee() == null || fixture.getReferee().isBlank()) {
            throw conflict("本场比赛尚未填写当值主裁");
        }
        return fixture;
    }

    private RefereeRatingSummary rounded(RefereeRatingSummary summary) {
        return new RefereeRatingSummary(summary.refereeName(),
                Math.round(summary.averageScore() * 10.0) / 10.0, summary.ratingCount(),
                summary.star1Count(), summary.star2Count(), summary.star3Count(),
                summary.star4Count(), summary.star5Count());
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
