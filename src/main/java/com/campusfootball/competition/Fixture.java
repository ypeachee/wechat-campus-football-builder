package com.campusfootball.competition;

import java.time.LocalDateTime;

public class Fixture {
    private Long id;
    private Long seasonId;
    private Integer roundNo;
    private Long homeTeamId;
    private Long awayTeamId;
    private LocalDateTime kickoffAt;
    private String venue;
    private FixtureStatus status;
    private Integer homeScore;
    private Integer awayScore;
    private boolean dataLocked;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSeasonId() { return seasonId; }
    public void setSeasonId(Long seasonId) { this.seasonId = seasonId; }
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
    public FixtureStatus getStatus() { return status; }
    public void setStatus(FixtureStatus status) { this.status = status; }
    public Integer getHomeScore() { return homeScore; }
    public void setHomeScore(Integer homeScore) { this.homeScore = homeScore; }
    public Integer getAwayScore() { return awayScore; }
    public void setAwayScore(Integer awayScore) { this.awayScore = awayScore; }
    public boolean isDataLocked() { return dataLocked; }
    public void setDataLocked(boolean dataLocked) { this.dataLocked = dataLocked; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
