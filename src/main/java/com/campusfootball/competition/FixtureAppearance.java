package com.campusfootball.competition;

import java.time.LocalDateTime;

public class FixtureAppearance {
    private Long id;
    private Long fixtureId;
    private Long teamId;
    private Long playerId;
    private boolean starter;
    private Integer minutesPlayed;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFixtureId() { return fixtureId; }
    public void setFixtureId(Long fixtureId) { this.fixtureId = fixtureId; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public boolean isStarter() { return starter; }
    public void setStarter(boolean starter) { this.starter = starter; }
    public Integer getMinutesPlayed() { return minutesPlayed; }
    public void setMinutesPlayed(Integer minutesPlayed) { this.minutesPlayed = minutesPlayed; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
