package com.campusfootball.announcement;

import java.time.LocalDateTime;

public class Announcement {
    private Long id;
    private String title;
    private String content;
    private AnnouncementAudience audience;
    private Long targetTeamId;
    private Long targetUserId;
    private Long createdBy;
    private Integer recipientCount;
    private Integer deliveredCount;
    private Integer readCount;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public AnnouncementAudience getAudience() { return audience; }
    public void setAudience(AnnouncementAudience audience) { this.audience = audience; }
    public Long getTargetTeamId() { return targetTeamId; }
    public void setTargetTeamId(Long targetTeamId) { this.targetTeamId = targetTeamId; }
    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Integer getRecipientCount() { return recipientCount; }
    public void setRecipientCount(Integer recipientCount) { this.recipientCount = recipientCount; }
    public Integer getDeliveredCount() { return deliveredCount; }
    public void setDeliveredCount(Integer deliveredCount) { this.deliveredCount = deliveredCount; }
    public Integer getReadCount() { return readCount; }
    public void setReadCount(Integer readCount) { this.readCount = readCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}