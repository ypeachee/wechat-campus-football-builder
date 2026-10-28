package com.campusfootball.competition;

public record DisciplineRule(long competitionId, int yellowSuspensionThreshold,
                             int redSuspensionMatches, boolean cardsCrossStages) {
    public static DisciplineRule defaults(long competitionId) {
        return new DisciplineRule(competitionId, 3, 1, true);
    }
}