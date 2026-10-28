package com.campusfootball.competition;

import java.time.LocalDateTime;

public class PlayerSuspension {
    private Long id;
    private Long playerId;
    private Long teamId;
    private Long seasonId;
    private SuspensionSourceType sourceType;
    private Long sourceEventId;
    private Long sourceFixtureId;
    private String reason;
    private Integer matchesTotal;
    private Integer matchesServed;
    private SuspensionStatus status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public Long getSeasonId() { return seasonId; }
    public void setSeasonId(Long seasonId) { this.seasonId = seasonId; }
    public SuspensionSourceType getSourceType() { return sourceType; }
    public void setSourceType(SuspensionSourceType sourceType) { this.sourceType = sourceType; }
    public Long getSourceEventId() { return sourceEventId; }
    public void setSourceEventId(Long sourceEventId) { this.sourceEventId = sourceEventId; }
    public Long getSourceFixtureId() { return sourceFixtureId; }
    public void setSourceFixtureId(Long sourceFixtureId) { this.sourceFixtureId = sourceFixtureId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getMatchesTotal() { return matchesTotal; }
    public void setMatchesTotal(Integer matchesTotal) { this.matchesTotal = matchesTotal; }
    public Integer getMatchesServed() { return matchesServed; }
    public void setMatchesServed(Integer matchesServed) { this.matchesServed = matchesServed; }
    public SuspensionStatus getStatus() { return status; }
    public void setStatus(SuspensionStatus status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}