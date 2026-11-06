package com.campusfootball.stats;

public record PlayerSeasonSummary(long playerId, long seasonId, String seasonName,
                                  long appearances, long starts, long substitutes, long minutesPlayed,
                                  long goals, long assists, long yellowCards, long redCards, long ownGoals) {
}
