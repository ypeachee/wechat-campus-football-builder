package com.campusfootball.notification;

import java.time.LocalDateTime;

public record UpcomingFixtureRow(long fixtureId, long homeTeamId, long awayTeamId,
                                 String homeTeamName, String awayTeamName,
                                 LocalDateTime kickoffAt) {
}