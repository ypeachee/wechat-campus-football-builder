package com.campusfootball.competition;

import java.time.LocalDateTime;

public class FixtureEvent {
    private Long id;
    private Long fixtureId;
    private Long teamId;
    private Long playerId;
    private EventType eventType;
    private Long relatedPlayerId;
    private Integer minute;
    private Integer stoppageMinute;
    private String note;
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
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public Long getRelatedPlayerId() { return relatedPlayerId; }
    public void setRelatedPlayerId(Long relatedPlayerId) { this.relatedPlayerId = relatedPlayerId; }
    public Integer getMinute() { return minute; }
    public void setMinute(Integer minute) { this.minute = minute; }
    public Integer getStoppageMinute() { return stoppageMinute; }
    public void setStoppageMinute(Integer stoppageMinute) { this.stoppageMinute = stoppageMinute; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
