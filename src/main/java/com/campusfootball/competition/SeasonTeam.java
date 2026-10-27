package com.campusfootball.competition;

public class SeasonTeam {
    private Long id;
    private Long seasonId;
    private Long teamId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSeasonId() { return seasonId; }
    public void setSeasonId(Long seasonId) { this.seasonId = seasonId; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
}
