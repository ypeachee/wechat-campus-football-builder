package com.campusfootball.notification;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class NotificationService {
    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    public Page list(long userId, int page, int size) {
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

    public void notifyUsers(java.util.Collection<Long> userIds, NotificationType type, String title,
                            String content, String linkUrl, String dedupeKey) {
        for (long userId : new LinkedHashSet<>(userIds)) {
            notifyUser(userId, type, title, content, linkUrl, dedupeKey);
        }
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

    public record Page(List<NotificationItem> items, int unreadCount, int page, int size) {
    }
}