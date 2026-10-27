package com.campusfootball.competition;

public class KnockoutTie {
    private Long id;
    private Long stageId;
    private Long roundId;
    private Integer bracketPosition;
    private Long homeTeamId;
    private Long awayTeamId;
    private Long winnerTeamId;
    private Long nextTieId;
    private BracketSlot nextSlot;
    private boolean thirdPlace;
    private TieStatus status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStageId() { return stageId; }
    public void setStageId(Long stageId) { this.stageId = stageId; }
    public Long getRoundId() { return roundId; }
    public void setRoundId(Long roundId) { this.roundId = roundId; }
    public Integer getBracketPosition() { return bracketPosition; }
    public void setBracketPosition(Integer bracketPosition) { this.bracketPosition = bracketPosition; }
    public Long getHomeTeamId() { return homeTeamId; }
    public void setHomeTeamId(Long homeTeamId) { this.homeTeamId = homeTeamId; }
    public Long getAwayTeamId() { return awayTeamId; }
    public void setAwayTeamId(Long awayTeamId) { this.awayTeamId = awayTeamId; }
    public Long getWinnerTeamId() { return winnerTeamId; }
    public void setWinnerTeamId(Long winnerTeamId) { this.winnerTeamId = winnerTeamId; }
    public Long getNextTieId() { return nextTieId; }
    public void setNextTieId(Long nextTieId) { this.nextTieId = nextTieId; }
    public BracketSlot getNextSlot() { return nextSlot; }
    public void setNextSlot(BracketSlot nextSlot) { this.nextSlot = nextSlot; }
    public boolean isThirdPlace() { return thirdPlace; }
    public void setThirdPlace(boolean thirdPlace) { this.thirdPlace = thirdPlace; }
    public TieStatus getStatus() { return status; }
    public void setStatus(TieStatus status) { this.status = status; }
}
