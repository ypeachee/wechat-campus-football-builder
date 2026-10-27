package com.campusfootball.rating;

import java.time.LocalDateTime;

public class SensitiveTerm {
    private Long id;
    private String term;
    private String category;
    private SensitiveTermAction action;
    private boolean enabled;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public SensitiveTermAction getAction() { return action; }
    public void setAction(SensitiveTermAction action) { this.action = action; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
