package com.campusfootball.announcement;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.notification.NotificationMapper;
import com.campusfootball.notification.NotificationService;
import com.campusfootball.notification.NotificationType;
import com.campusfootball.team.AuthorizationService;
import com.campusfootball.team.TeamMapper;
import com.campusfootball.user.AppUser;
import com.campusfootball.user.AppUserMapper;
import com.campusfootball.user.UserRole;
import com.campusfootball.user.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnnouncementService {
    private final AnnouncementMapper announcementMapper;
    private final AppUserMapper appUserMapper;
    private final NotificationMapper notificationMapper;
    private final NotificationService notificationService;
    private final TeamMapper teamMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public AnnouncementService(AnnouncementMapper announcementMapper, AppUserMapper appUserMapper,
                               NotificationMapper notificationMapper, NotificationService notificationService,
                               TeamMapper teamMapper, AuthorizationService authorizationService,
                               AuditService auditService) {
        this.announcementMapper = announcementMapper;
        this.appUserMapper = appUserMapper;
        this.notificationMapper = notificationMapper;
        this.notificationService = notificationService;
        this.teamMapper = teamMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    public Page list(long actorUserId, int page, int size) {
        UserRole role = authorizationService.currentRole(actorUserId);
        if (role != UserRole.ADMIN && role != UserRole.TEAM_MANAGER) {
            throw forbidden();
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(50, size));
        Long createdBy = role == UserRole.ADMIN ? null : actorUserId;
        return new Page(announcementMapper.findPage(createdBy, safeSize, safePage * safeSize).stream()
                .map(this::view).toList(),
                announcementMapper.count(createdBy), safePage, safeSize);
    }

    public AnnouncementView detail(long actorUserId, long announcementId) {
        UserRole role = authorizationService.currentRole(actorUserId);
        if (role != UserRole.ADMIN && role != UserRole.TEAM_MANAGER) {
            throw forbidden();
        }
        Announcement announcement = announcementMapper.findById(announcementId)
                .orElseThrow(() -> notFound("公告不存在"));
        if (role == UserRole.TEAM_MANAGER && !announcement.getCreatedBy().equals(actorUserId)) {
            throw forbidden();
        }
        return view(announcement);
    }
    @Transactional
    public AnnouncementView create(long actorUserId, String title, String content,
                                   AnnouncementAudience audience, Long targetTeamId, Long targetUserId) {
        UserRole role = authorizationService.currentRole(actorUserId);
        if (role != UserRole.ADMIN && role != UserRole.TEAM_MANAGER) {
            throw forbidden();
        }
        String normalizedTitle = title == null ? "" : title.trim();
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedTitle.isBlank() || normalizedTitle.length() > 120) {
            throw badRequest("公告标题必须为 1–120 个字符");
        }
        if (normalizedContent.isBlank() || normalizedContent.length() > 500) {
            throw badRequest("公告正文必须为 1–500 个字符");
        }
        if (audience == null) {
            throw badRequest("请选择公告范围");
        }
        if (role == UserRole.TEAM_MANAGER
                && (audience != AnnouncementAudience.TEAM || targetTeamId == null)) {
            throw forbidden();
        }
        if (audience == AnnouncementAudience.TEAM) {
            teamMapper.findById(targetTeamId == null ? 0 : targetTeamId)
                    .orElseThrow(() -> notFound("球队不存在"));
        } else if (targetTeamId != null) {
            throw badRequest("当前公告范围不能指定球队");
        }
        if (audience == AnnouncementAudience.USER) {
            AppUser target = appUserMapper.findById(targetUserId == null ? 0 : targetUserId)
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .orElseThrow(() -> notFound("目标用户不存在或已停用"));
            if (target.getRoleCode() == UserRole.VIEWER && role == UserRole.TEAM_MANAGER) {
                throw forbidden();
            }
        } else if (targetUserId != null) {
            throw badRequest("当前公告范围不能指定用户");
        }

        List<Long> recipients = recipients(audience, targetTeamId, targetUserId);
        Announcement announcement = new Announcement();
        announcement.setTitle(normalizedTitle);
        announcement.setContent(normalizedContent);
        announcement.setAudience(audience);
        announcement.setTargetTeamId(targetTeamId);
        announcement.setTargetUserId(targetUserId);
        announcement.setCreatedBy(actorUserId);
        announcement.setRecipientCount(recipients.size());
        announcementMapper.insert(announcement);

        if (!recipients.isEmpty()) {
            notificationService.notifyUsers(recipients, NotificationType.SYSTEM,
                    normalizedTitle, normalizedContent, "/pages/notifications/index",
                    "announcement:" + announcement.getId());
        }
        auditService.record(actorUserId, targetTeamId, AuditAction.CREATE, "ANNOUNCEMENT",
                announcement.getId(), null, announcement);
        return view(announcementMapper.findById(announcement.getId()).orElseThrow());
    }
    private List<Long> recipients(AnnouncementAudience audience, Long teamId, Long userId) {
        return switch (audience) {
            case ALL -> appUserMapper.findActiveIds();
            case ADMIN -> appUserMapper.findActiveIdsByRole(UserRole.ADMIN);
            case TEAM_STAFF -> notificationMapper.findAllTeamStaffUserIds();
            case TEAM -> notificationMapper.findTeamStaffUserIds(teamId);
            case USER -> List.of(userId);
        };
    }

    private AnnouncementView view(Announcement announcement) {
        return new AnnouncementView(announcement.getId(), announcement.getTitle(), announcement.getContent(),
                announcement.getAudience(), announcement.getTargetTeamId(), announcement.getTargetUserId(),
                announcement.getCreatedBy(), announcement.getRecipientCount(),
                announcement.getDeliveredCount() == null ? 0 : announcement.getDeliveredCount(),
                announcement.getReadCount() == null ? 0 : announcement.getReadCount(),
                announcement.getCreatedAt());
    }

    private AppException badRequest(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }

    private AppException forbidden() {
        return new AppException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "没有权限执行该操作");
    }

    private AppException notFound(String message) {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    public record AnnouncementView(long id, String title, String content, AnnouncementAudience audience,
                                   Long targetTeamId, Long targetUserId, long createdBy,
                                   int recipientCount, int deliveredCount, int readCount,
                                   LocalDateTime createdAt) {
    }

    public record Page(List<AnnouncementView> items, long total, int page, int size) {
    }
}