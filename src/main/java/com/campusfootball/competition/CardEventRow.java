package com.campusfootball.competition;

import java.time.LocalDateTime;

public class CardEventRow {
    private Long eventId;
    private Long fixtureId;
    private Long stageId;
    private Long teamId;
    private Long playerId;
    private EventType eventType;
    private Integer minute;
    private LocalDateTime kickoffAt;
    private Long eventCreatedBy;

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public Long getFixtureId() { return fixtureId; }
    public void setFixtureId(Long fixtureId) { this.fixtureId = fixtureId; }
    public Long getStageId() { return stageId; }
    public void setStageId(Long stageId) { this.stageId = stageId; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public Integer getMinute() { return minute; }
    public void setMinute(Integer minute) { this.minute = minute; }
    public LocalDateTime getKickoffAt() { return kickoffAt; }
    public void setKickoffAt(LocalDateTime kickoffAt) { this.kickoffAt = kickoffAt; }
    public Long getEventCreatedBy() { return eventCreatedBy; }
    public void setEventCreatedBy(Long eventCreatedBy) { this.eventCreatedBy = eventCreatedBy; }
}