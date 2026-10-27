package com.campusfootball.competition;

public class GroupTeam {
    private Long id;
    private Long groupId;
    private Long teamId;
    private Integer seedNo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }
    public Integer getSeedNo() { return seedNo; }
    public void setSeedNo(Integer seedNo) { this.seedNo = seedNo; }
}
