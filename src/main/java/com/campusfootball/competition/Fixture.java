package com.campusfootball.competition;

import java.time.LocalDateTime;

public class Fixture {
    private Long id;
    private Long seasonId;
    private Long stageId;
    private Long groupId;
    private Long knockoutTieId;
    private Integer roundNo;
    private Long homeTeamId;
    private Long awayTeamId;
    private LocalDateTime kickoffAt;
    private String venue;
    private String referee;
    private FixtureStatus status;
    private Integer homeScore;
    private Integer awayScore;
    private Integer homePenaltyScore;
    private Integer awayPenaltyScore;
    private boolean dataLocked;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSeasonId() { return seasonId; }
    public void setSeasonId(Long seasonId) { this.seasonId = seasonId; }
    public Long getStageId() { return stageId; }
    public void setStageId(Long stageId) { this.stageId = stageId; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public Long getKnockoutTieId() { return knockoutTieId; }
    public void setKnockoutTieId(Long knockoutTieId) { this.knockoutTieId = knockoutTieId; }
    public Integer getRoundNo() { return roundNo; }
    public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }
    public Long getHomeTeamId() { return homeTeamId; }
    public void setHomeTeamId(Long homeTeamId) { this.homeTeamId = homeTeamId; }
    public Long getAwayTeamId() { return awayTeamId; }
    public void setAwayTeamId(Long awayTeamId) { this.awayTeamId = awayTeamId; }
    public LocalDateTime getKickoffAt() { return kickoffAt; }
    public void setKickoffAt(LocalDateTime kickoffAt) { this.kickoffAt = kickoffAt; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public String getReferee() { return referee; }
    public void setReferee(String referee) { this.referee = referee; }
    public FixtureStatus getStatus() { return status; }
    public void setStatus(FixtureStatus status) { this.status = status; }
    public Integer getHomeScore() { return homeScore; }
    public void setHomeScore(Integer homeScore) { this.homeScore = homeScore; }
    public Integer getAwayScore() { return awayScore; }
    public void setAwayScore(Integer awayScore) { this.awayScore = awayScore; }
    public Integer getHomePenaltyScore() { return homePenaltyScore; }
    public void setHomePenaltyScore(Integer homePenaltyScore) { this.homePenaltyScore = homePenaltyScore; }
    public Integer getAwayPenaltyScore() { return awayPenaltyScore; }
    public void setAwayPenaltyScore(Integer awayPenaltyScore) { this.awayPenaltyScore = awayPenaltyScore; }
    public boolean isDataLocked() { return dataLocked; }
    public void setDataLocked(boolean dataLocked) { this.dataLocked = dataLocked; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
