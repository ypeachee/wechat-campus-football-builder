package com.campusfootball.team;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Player {
    private Long id;
    private Long teamId;
    private String name;
    private String jerseyNo;
    private String position;
    private LocalDate birthDate;
    private String photoUrl;
    private PlayerStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getJerseyNo() { return jerseyNo; }
    public void setJerseyNo(String jerseyNo) { this.jerseyNo = jerseyNo; }
    public void setJerseyNo(Integer jerseyNo) { this.jerseyNo = jerseyNo == null ? null : jerseyNo.toString(); }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public PlayerStatus getStatus() { return status; }
    public void setStatus(PlayerStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
