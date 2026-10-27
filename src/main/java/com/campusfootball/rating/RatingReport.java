package com.campusfootball.rating;

import java.time.LocalDateTime;

public class RatingReport {
    private Long id;
    private Long ratingId;
    private Long reporterUserId;
    private String reason;
    private RatingReportStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRatingId() { return ratingId; }
    public void setRatingId(Long ratingId) { this.ratingId = ratingId; }
    public Long getReporterUserId() { return reporterUserId; }
    public void setReporterUserId(Long reporterUserId) { this.reporterUserId = reporterUserId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public RatingReportStatus getStatus() { return status; }
    public void setStatus(RatingReportStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
