package com.campusfootball.stats;

import java.util.List;

public record PlayerSeasonStats(long playerId, long seasonId, String seasonName,
                                long appearances, long starts, long substitutes, long minutesPlayed,
                                long goals, long assists, long yellowCards, long redCards, long ownGoals,
                                List<PlayerMatchStat> matches) {
}
