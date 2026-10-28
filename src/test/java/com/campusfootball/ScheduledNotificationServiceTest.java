package com.campusfootball;

import com.campusfootball.notification.NotificationMapper;
import com.campusfootball.notification.NotificationService;
import com.campusfootball.notification.NotificationType;
import com.campusfootball.notification.ScheduledNotificationService;
import com.campusfootball.notification.UpcomingFixtureRow;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledNotificationServiceTest {
    private final NotificationMapper mapper = mock(NotificationMapper.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T08:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final ScheduledNotificationService service = new ScheduledNotificationService(
            mapper, notifications, clock, true);
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 28, 16, 0);

    @Test
    void sendsOnlyStartReminderOutsideTwoHourWindow() {
        when(mapper.findScheduledBetween(now, now.plusHours(24)))
                .thenReturn(List.of(new UpcomingFixtureRow(8, 1, 2, "蓝队", "红队", now.plusHours(3))));

        service.runAt(now);

        verify(notifications, times(2)).notifyTeamStaff(
                org.mockito.ArgumentMatchers.anyLong(), eq(NotificationType.FIXTURE_START),
                eq("比赛即将开始"), contains("蓝队 vs 红队"), contains("fixture-detail"), eq("fixture-start:8"));
        verify(notifications, never()).notifyTeamStaff(
                org.mockito.ArgumentMatchers.anyLong(), eq(NotificationType.LINEUP_DEADLINE),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void sendsLineupReminderInsideTwoHourWindow() {
        when(mapper.findScheduledBetween(now, now.plusHours(24)))
                .thenReturn(List.of(new UpcomingFixtureRow(9, 1, 2, "蓝队", "红队", now.plusMinutes(90))));

        service.runAt(now);

        verify(notifications, times(2)).notifyTeamStaff(
                org.mockito.ArgumentMatchers.anyLong(), eq(NotificationType.LINEUP_DEADLINE),
                eq("阵容确认提醒"), contains("请及时确认"), contains("manage-fixture-data"),
                eq("lineup-deadline:9"));
    }
}