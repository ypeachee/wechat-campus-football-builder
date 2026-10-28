package com.campusfootball.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ScheduledNotificationService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final NotificationMapper notificationMapper;
    private final NotificationService notificationService;
    private final Clock clock;
    private final boolean scheduledEnabled;

    public ScheduledNotificationService(NotificationMapper notificationMapper,
                                        NotificationService notificationService,
                                        Clock clock,
                                        @Value("${app.notifications.scheduled-enabled:true}") boolean scheduledEnabled) {
        this.notificationMapper = notificationMapper;
        this.notificationService = notificationService;
        this.clock = clock;
        this.scheduledEnabled = scheduledEnabled;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Shanghai")
    public void runHourly() {
        runAt(LocalDateTime.now(clock));
    }

    public ReminderRunResult runAt(LocalDateTime now) {
        List<UpcomingFixtureRow> fixtures = notificationMapper.findScheduledBetween(now, now.plusHours(24));
        int startReminders = 0;
        int lineupReminders = 0;
        for (UpcomingFixtureRow fixture : fixtures) {
            String teams = fixture.homeTeamName() + " vs " + fixture.awayTeamName();
            String kickoff = fixture.kickoffAt().format(TIME_FORMAT);
            notifyFixtureStaff(fixture, NotificationType.FIXTURE_START,
                    "比赛即将开始", teams + " " + kickoff + " 开赛",
                    "/pages/fixture-detail/index?id=" + fixture.fixtureId(),
                    "fixture-start:" + fixture.fixtureId());
            startReminders++;

            if (!fixture.kickoffAt().isAfter(now.plusHours(2))) {
                notifyFixtureStaff(fixture, NotificationType.LINEUP_DEADLINE,
                        "阵容确认提醒", teams + " 请及时确认首发与替补名单",
                        "/pages/manage-fixture-data/index",
                        "lineup-deadline:" + fixture.fixtureId());
                lineupReminders++;
            }
        }
        return new ReminderRunResult(startReminders, lineupReminders, fixtures.size());
    }

    private void notifyFixtureStaff(UpcomingFixtureRow fixture, NotificationType type,
                                    String title, String content, String link, String dedupeKey) {
        notificationService.notifyTeamStaff(fixture.homeTeamId(), type, title, content, link, dedupeKey);
        notificationService.notifyTeamStaff(fixture.awayTeamId(), type, title, content, link, dedupeKey);
    }

    public record ReminderRunResult(int startReminders, int lineupReminders, int fixturesChecked) {
    }
}