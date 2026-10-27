package com.campusfootball.stats;

public class StatsFilter {
    private final Long competitionId;
    private final Long seasonId;
    private final Long stageId;
    private final Long groupId;

    public StatsFilter(Long competitionId, Long seasonId, Long stageId, Long groupId) {
        this.competitionId = competitionId;
        this.seasonId = seasonId;
        this.stageId = stageId;
        this.groupId = groupId;
    }

    public Long getCompetitionId() { return competitionId; }
    public Long getSeasonId() { return seasonId; }
    public Long getStageId() { return stageId; }
    public Long getGroupId() { return groupId; }
}
