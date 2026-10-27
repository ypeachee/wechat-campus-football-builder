package com.campusfootball.rating;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.team.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RatingModerationService {
    private final RatingMapper ratingMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final Clock clock;

    public RatingModerationService(RatingMapper ratingMapper, AuthorizationService authorizationService,
                                   AuditService auditService, Clock clock) {
        this.ratingMapper = ratingMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    public Page list(long actorUserId, String filter, int page, int size) {
        authorizationService.requireAdmin(actorUserId);
        String normalized = filter == null ? "ALL" : filter.toUpperCase();
        if (!List.of("ALL", "PENDING", "REPORTED", "HIDDEN").contains(normalized)) {
            throw badRequest("不支持的审核筛选条件");
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        return new Page(ratingMapper.countForModeration(normalized),
                ratingMapper.findForModeration(normalized, safeSize, safePage * safeSize));
    }

    @Transactional
    public void moderate(long actorUserId, long ratingId, RatingStatus target, String reason) {
        authorizationService.requireAdmin(actorUserId);
        if (target == null || target == RatingStatus.PENDING) {
            throw badRequest("目标审核状态无效");
        }
        PlayerMatchRating before = ratingMapper.findById(ratingId).orElseThrow(() -> notFound("评分不存在"));
        String cleanedReason = reason == null ? null : reason.trim();
        if (cleanedReason != null && cleanedReason.length() > 100) {
            throw badRequest("审核原因不能超过 100 个字符");
        }
        ratingMapper.updateStatus(ratingId, target, cleanedReason, LocalDateTime.now(clock));
        ratingMapper.resolveOpenReports(ratingId, RatingReportStatus.REVIEWED);
        PlayerMatchRating after = ratingMapper.findById(ratingId).orElseThrow();
        auditService.record(actorUserId, after.getTeamId(), AuditAction.MODERATE, "PLAYER_RATING",
                ratingId, before, after);
    }

    private AppException badRequest(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }

    private AppException notFound(String message) {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    public record Page(long total, List<RatingModerationView> items) {
    }
}
