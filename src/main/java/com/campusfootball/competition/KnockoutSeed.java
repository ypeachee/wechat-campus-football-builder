package com.campusfootball.competition;

public class KnockoutSeed {
    private Long id;
    private Long stageId;
    private Long sourceGroupId;
    private Integer sourceRank;
    private Long targetTieId;
    private BracketSlot targetSlot;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStageId() { return stageId; }
    public void setStageId(Long stageId) { this.stageId = stageId; }
    public Long getSourceGroupId() { return sourceGroupId; }
    public void setSourceGroupId(Long sourceGroupId) { this.sourceGroupId = sourceGroupId; }
    public Integer getSourceRank() { return sourceRank; }
    public void setSourceRank(Integer sourceRank) { this.sourceRank = sourceRank; }
    public Long getTargetTieId() { return targetTieId; }
    public void setTargetTieId(Long targetTieId) { this.targetTieId = targetTieId; }
    public BracketSlot getTargetSlot() { return targetSlot; }
    public void setTargetSlot(BracketSlot targetSlot) { this.targetSlot = targetSlot; }
}
