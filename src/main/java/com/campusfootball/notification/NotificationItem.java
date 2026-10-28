package com.campusfootball.notification;

import java.time.LocalDateTime;

public record NotificationItem(long id, NotificationType type, String title, String content,
                               String linkUrl, LocalDateTime readAt, LocalDateTime createdAt) {
    public boolean isRead() {
        return readAt != null;
    }

    public boolean getRead() {
        return readAt != null;
    }
}