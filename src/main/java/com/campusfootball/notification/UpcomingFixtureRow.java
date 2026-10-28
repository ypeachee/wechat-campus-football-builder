package com.campusfootball.notification;

import java.time.LocalDateTime;

public record UpcomingFixtureRow(long fixtureId, String homeTeamName, String awayTeamName,
                                 LocalDateTime kickoffAt) {
}