package com.campusfootball.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class NotificationService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    @Transactional
    public Page list(long userId, int page, int size) {
        ensureUpcomingNotifications(userId);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(50, size));
        List<NotificationItem> items = notificationMapper.findRecent(userId, safeSize, safePage * safeSize);
        return new Page(items, notificationMapper.countUnread(userId), safePage, safeSize);
    }

    public int unreadCount(long userId) {
        return notificationMapper.countUnread(userId);
    }

    public void markRead(long userId, long notificationId) {
        notificationMapper.markRead(userId, notificationId);
    }

    public void markAllRead(long userId) {
        notificationMapper.markAllRead(userId);
    }

    public void notifyAdmins(NotificationType type, String title, String content,
                             String linkUrl, String dedupeKey) {
        for (long userId : new LinkedHashSet<>(notificationMapper.findAdminUserIds())) {
            notifyUser(userId, type, title, content, linkUrl, dedupeKey);
        }
    }

    public void notifyTeamStaff(long teamId, NotificationType type, String title, String content,
                                String linkUrl, String dedupeKey) {
        List<Long> recipients = new ArrayList<>(notificationMapper.findTeamStaffUserIds(teamId));
        recipients.addAll(notificationMapper.findAdminUserIds());
        for (long userId : new LinkedHashSet<>(recipients)) {
            notifyUser(userId, type, title, content, linkUrl, dedupeKey);
        }
    }

    private void notifyUser(long userId, NotificationType type, String title, String content,
                            String linkUrl, String dedupeKey) {
        notificationMapper.insertIgnore(userId, type, title, content, linkUrl, dedupeKey);
    }

    private void ensureUpcomingNotifications(long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = now.plusHours(24);
        for (UpcomingFixtureRow fixture : notificationMapper.findUpcomingFixturesForUser(userId, now, until)) {
            String teams = fixture.homeTeamName() + " vs " + fixture.awayTeamName();
            String kickoff = fixture.kickoffAt() == null ? "" : fixture.kickoffAt().format(TIME_FORMAT);
            notifyUser(userId, NotificationType.FIXTURE_START,
                    "比赛即将开始", teams + " " + kickoff + " 开赛",
                    "/pages/fixture-detail/index?id=" + fixture.fixtureId(),
                    "fixture-start:" + fixture.fixtureId());
            notifyUser(userId, NotificationType.LINEUP_DEADLINE,
                    "阵容确认提醒", teams + " 请及时确认首发与替补名单",
                    "/pages/manage-fixture-data/index",
                    "lineup-deadline:" + fixture.fixtureId());
        }
    }

    public record Page(List<NotificationItem> items, int unreadCount, int page, int size) {
    }
}