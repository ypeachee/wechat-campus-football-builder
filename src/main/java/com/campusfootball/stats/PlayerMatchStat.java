package com.campusfootball.stats;

import java.time.LocalDateTime;

public record PlayerMatchStat(long fixtureId, Integer roundNo, LocalDateTime kickoffAt,
                              long opponentTeamId, String opponentTeamName,
                              Integer scoreFor, Integer scoreAgainst,
                              boolean starter, Integer minutesPlayed,
                              long goals, long assists, long yellowCards, long redCards, long ownGoals) {
}
