package com.campusfootball.rating;

import java.time.LocalDateTime;

public class PlayerMatchRating {
    private Long id;
    private Long fixtureId;
    private Long playerId;
    private Long teamId;
    private Long userId;
    private Integer stars;
    private String comment;
    private RatingStatus status;
    private String moderationReason;
    private Integer editsUsed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFixtureId() { return fixtureId; }
    public void setFixtureId(Long fixtureId) { this.fixtureId = fixtureId; }
    public Long getPlayerId() { return playerId; }
    public void setPlayerId(Long playerId) { this.playerId = playerId; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getStars() { return stars; }
    public void setStars(Integer stars) { this.stars = stars; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public RatingStatus getStatus() { return status; }
    public void setStatus(RatingStatus status) { this.status = status; }
    public String getModerationReason() { return moderationReason; }
    public void setModerationReason(String moderationReason) { this.moderationReason = moderationReason; }
    public Integer getEditsUsed() { return editsUsed; }
    public void setEditsUsed(Integer editsUsed) { this.editsUsed = editsUsed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
