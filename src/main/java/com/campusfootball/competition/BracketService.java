package com.campusfootball.competition;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.stats.StatsFilter;
import com.campusfootball.stats.StatsService;
import com.campusfootball.stats.StandingRow;
import com.campusfootball.team.AuthorizationService;
import com.campusfootball.team.Team;
import com.campusfootball.team.TeamMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BracketService {
    private final KnockoutMapper knockoutMapper;
    private final StageMapper stageMapper;
    private final GroupMapper groupMapper;
    private final FixtureMapper fixtureMapper;
    private final TeamMapper teamMapper;
    private final StatsService statsService;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public BracketService(KnockoutMapper knockoutMapper, StageMapper stageMapper, GroupMapper groupMapper,
                          FixtureMapper fixtureMapper, TeamMapper teamMapper, StatsService statsService,
                          AuthorizationService authorizationService, AuditService auditService) {
        this.knockoutMapper = knockoutMapper;
        this.stageMapper = stageMapper;
        this.groupMapper = groupMapper;
        this.fixtureMapper = fixtureMapper;
        this.teamMapper = teamMapper;
        this.statsService = statsService;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @Transactional
    public long createRound(long actorUserId, long stageId, int roundNo, String name, int sortOrder) {
        authorizationService.requireAdmin(actorUserId);
        Stage stage = requireStage(stageId, StageType.KNOCKOUT);
        if (roundNo < 1) throw conflict("轮次必须大于 0");
        KnockoutRound round = new KnockoutRound();
        round.setStageId(stage.getId());
        round.setRoundNo(roundNo);
        round.setName(name);
        round.setSortOrder(sortOrder);
        knockoutMapper.insertRound(round);
        return round.getId();
    }

    @Transactional
    public long createTie(long actorUserId, long stageId, long roundId, int bracketPosition,
                          Long homeTeamId, Long awayTeamId, Long nextTieId, BracketSlot nextSlot,
                          boolean thirdPlace) {
        authorizationService.requireAdmin(actorUserId);
        requireStage(stageId, StageType.KNOCKOUT);
        KnockoutRound round = knockoutMapper.findRoundById(roundId).orElseThrow(() -> notFound("轮次不存在"));
        if (round.getStageId() != stageId) throw conflict("轮次不属于该阶段");
        KnockoutTie tie = new KnockoutTie();
        tie.setStageId(stageId);
        tie.setRoundId(roundId);
        tie.setBracketPosition(bracketPosition);
        tie.setHomeTeamId(homeTeamId);
        tie.setAwayTeamId(awayTeamId);
        tie.setNextTieId(nextTieId);
        tie.setNextSlot(nextSlot);
        tie.setThirdPlace(thirdPlace);
        tie.setStatus(homeTeamId != null && awayTeamId != null ? TieStatus.READY : TieStatus.PENDING);
        knockoutMapper.insertTie(tie);
        return tie.getId();
    }

    @Transactional
    public void saveSeed(long actorUserId, long stageId, long groupId, int sourceRank,
                         long targetTieId, BracketSlot slot) {
        authorizationService.requireAdmin(actorUserId);
        requireStage(stageId, StageType.KNOCKOUT);
        CompetitionGroup group = groupMapper.findById(groupId).orElseThrow(() -> notFound("小组不存在"));
        KnockoutTie tie = knockoutMapper.findTieById(targetTieId).orElseThrow(() -> notFound("对阵不存在"));
        List<KnockoutTie> firstRound = knockoutMapper.findFirstRoundTies(stageId);
        if (firstRound.stream().noneMatch(item -> item.getId() == targetTieId)) {
            throw conflict("种子只能指向首轮对阵");
        }
        if (tie.getStageId() != stageId) throw conflict("对阵不属于该阶段");
        KnockoutSeed seed = new KnockoutSeed();
        seed.setStageId(stageId);
        seed.setSourceGroupId(group.getId());
        seed.setSourceRank(sourceRank);
        seed.setTargetTieId(targetTieId);
        seed.setTargetSlot(slot);
        knockoutMapper.insertSeed(seed);
    }

    @Transactional
    public long createTieFixture(long actorUserId, long tieId, LocalDateTime kickoffAt, String venue) {
        authorizationService.requireAdmin(actorUserId);
        KnockoutTie tie = knockoutMapper.findTieById(tieId).orElseThrow(() -> notFound("对阵不存在"));
        if (tie.getHomeTeamId() == null || tie.getAwayTeamId() == null) {
            throw conflict("对阵双方尚未确定");
        }
        if (fixtureMapper.findByKnockoutTieId(tieId).isPresent()) {
            throw conflict("该对阵已经有比赛");
        }
        Stage stage = requireStage(tie.getStageId(), StageType.KNOCKOUT);
        KnockoutRound round = knockoutMapper.findRoundById(tie.getRoundId()).orElseThrow(() -> notFound("轮次不存在"));
        Fixture fixture = new Fixture();
        fixture.setSeasonId(stage.getSeasonId());
        fixture.setStageId(stage.getId());
        fixture.setKnockoutTieId(tieId);
        fixture.setRoundNo(round.getRoundNo());
        fixture.setHomeTeamId(tie.getHomeTeamId());
        fixture.setAwayTeamId(tie.getAwayTeamId());
        fixture.setKickoffAt(kickoffAt);
        fixture.setVenue(venue);
        fixture.setStatus(FixtureStatus.DRAFT);
        fixture.setDataLocked(false);
        fixture.setCreatedBy(actorUserId);
        fixtureMapper.insert(fixture);
        return fixture.getId();
    }

    @Transactional
    public BracketView generateBracket(long actorUserId, long knockoutStageId, long groupStageId,
                                       int qualifiersPerGroup, boolean thirdPlace) {
        authorizationService.requireAdmin(actorUserId);
        Stage knockoutStage = requireStage(knockoutStageId, StageType.KNOCKOUT);
        Stage groupStage = requireStage(groupStageId, StageType.GROUP);
        if (knockoutStage.getSeasonId().longValue() != groupStage.getSeasonId().longValue()) {
            throw conflict("小组赛和淘汰赛必须属于同一赛季");
        }
        if (!knockoutMapper.findRounds(knockoutStageId).isEmpty()) {
            throw conflict("该淘汰赛阶段已经生成对阵");
        }
        if (qualifiersPerGroup < 1 || qualifiersPerGroup > 2) {
            throw conflict("每组晋级数量目前只支持 1 或 2");
        }
        List<CompetitionGroup> groups = groupMapper.findByStage(groupStageId);
        List<Qualifier> qualifiers = new ArrayList<>();
        for (CompetitionGroup group : groups) {
            List<StandingRow> rows = statsService.standings(
                    new StatsFilter(null, groupStage.getSeasonId(), groupStageId, group.getId()));
            for (int i = 0; i < Math.min(qualifiersPerGroup, rows.size()); i++) {
                qualifiers.add(new Qualifier(group.getId(), i + 1, rows.get(i).teamId()));
            }
        }
        int size = qualifiers.size();
        if (size != 4 && size != 8 && size != 16) {
            throw conflict("晋级球队数量必须是 4、8 或 16");
        }
        int roundCount = Integer.numberOfTrailingZeros(size);
        Map<Integer, Long> roundIds = new HashMap<>();
        for (int r = 1; r <= roundCount; r++) {
            KnockoutRound round = new KnockoutRound();
            round.setStageId(knockoutStageId);
            round.setRoundNo(r);
            round.setName(roundName(r, roundCount));
            round.setSortOrder(r);
            knockoutMapper.insertRound(round);
            roundIds.put(r, round.getId());
        }
        Map<String, KnockoutTie> ties = new HashMap<>();
        for (int r = roundCount; r >= 1; r--) {
            int count = size / (1 << r);
            for (int pos = 1; pos <= count; pos++) {
                Long nextTieId = null;
                BracketSlot nextSlot = null;
                if (r < roundCount) {
                    int nextPos = (pos + 1) / 2;
                    nextTieId = ties.get(r + 1 + ":" + nextPos).getId();
                    nextSlot = pos % 2 == 1 ? BracketSlot.HOME : BracketSlot.AWAY;
                }
                KnockoutTie tie = new KnockoutTie();
                tie.setStageId(knockoutStageId);
                tie.setRoundId(roundIds.get(r));
                tie.setBracketPosition(pos);
                tie.setNextTieId(nextTieId);
                tie.setNextSlot(nextSlot);
                tie.setStatus(TieStatus.PENDING);
                knockoutMapper.insertTie(tie);
                ties.put(r + ":" + pos, tie);
            }
        }
        int firstRoundTies = size / 2;
        for (int i = 0; i < firstRoundTies; i++) {
            Qualifier home = qualifiers.get(i);
            Qualifier away = qualifiers.get(i + firstRoundTies);
            KnockoutTie tie = ties.get("1:" + (i + 1));
            tie.setHomeTeamId(home.teamId());
            tie.setAwayTeamId(away.teamId());
            tie.setStatus(TieStatus.READY);
            knockoutMapper.updateTie(tie);
            insertSeed(knockoutStageId, home, tie.getId(), BracketSlot.HOME);
            insertSeed(knockoutStageId, away, tie.getId(), BracketSlot.AWAY);
        }
        if (thirdPlace) {
            KnockoutRound round = new KnockoutRound();
            round.setStageId(knockoutStageId);
            round.setRoundNo(roundCount + 1);
            round.setName("三四名决赛");
            round.setSortOrder(roundCount + 1);
            knockoutMapper.insertRound(round);
            KnockoutTie tie = new KnockoutTie();
            tie.setStageId(knockoutStageId);
            tie.setRoundId(round.getId());
            tie.setBracketPosition(1);
            tie.setThirdPlace(true);
            tie.setStatus(TieStatus.PENDING);
            knockoutMapper.insertTie(tie);
        }
        auditService.record(actorUserId, null, AuditAction.CREATE, "BRACKET", knockoutStageId, null,
                new BracketSnapshot(knockoutStageId, groupStageId, size, thirdPlace));
        return getBracket(knockoutStageId);
    }

    public BracketView getBracket(long stageId) {
        Stage stage = requireStage(stageId, StageType.KNOCKOUT);
        List<KnockoutRound> rounds = knockoutMapper.findRounds(stageId);
        List<KnockoutTie> allTies = knockoutMapper.findTies(stageId);
        List<BracketRoundView> roundViews = new ArrayList<>();
        BracketTieView thirdPlaceView = null;
        for (KnockoutRound round : rounds) {
            List<BracketTieView> ties = allTies.stream()
                    .filter(tie -> tie.getRoundId().equals(round.getId()))
                    .sorted(Comparator.comparingInt(KnockoutTie::getBracketPosition))
                    .map(this::toView).toList();
            roundViews.add(new BracketRoundView(round.getId(), round.getRoundNo(), round.getName(), ties));
            for (BracketTieView view : ties) if (view.thirdPlace()) thirdPlaceView = view;
        }
        TeamRef champion = null;
        int maxRound = rounds.stream().filter(round -> !"三四名决赛".equals(round.getName()))
                .mapToInt(KnockoutRound::getRoundNo).max().orElse(0);
        for (KnockoutTie tie : allTies) {
            KnockoutRound round = rounds.stream().filter(r -> r.getId().equals(tie.getRoundId())).findFirst().orElse(null);
            if (round != null && round.getRoundNo() == maxRound && !tie.isThirdPlace() && tie.getWinnerTeamId() != null) {
                champion = teamRef(tie.getWinnerTeamId());
            }
        }
        return new BracketView(stage.getId(), stage.getName(), roundViews, thirdPlaceView, champion);
    }

    @Transactional
    public void finishTie(long actorUserId, long tieId, int homeScore, int awayScore,
                          Integer homePenaltyScore, Integer awayPenaltyScore) {
        KnockoutTie tie = knockoutMapper.findTieById(tieId).orElseThrow(() -> notFound("对阵不存在"));
        if (tie.getHomeTeamId() == null || tie.getAwayTeamId() == null) throw conflict("对阵双方尚未确定");
        long winnerId;
        if (homeScore > awayScore) winnerId = tie.getHomeTeamId();
        else if (homeScore < awayScore) winnerId = tie.getAwayTeamId();
        else if (homePenaltyScore == null || awayPenaltyScore == null || homePenaltyScore.equals(awayPenaltyScore)) {
            throw conflict("淘汰赛平局必须提供不同的点球比分");
        } else winnerId = homePenaltyScore > awayPenaltyScore ? tie.getHomeTeamId() : tie.getAwayTeamId();

        if (tie.getWinnerTeamId() != null && tie.getWinnerTeamId() != winnerId && tie.getNextTieId() != null) {
            KnockoutTie next = knockoutMapper.findTieById(tie.getNextTieId()).orElse(null);
            if (next != null && fixtureMapper.findByKnockoutTieId(next.getId()).isPresent()) {
                throw conflict("下一轮比赛已存在，不能修改当前晋级结果");
            }
        }
        tie.setWinnerTeamId(winnerId);
        tie.setStatus(TieStatus.FINISHED);
        knockoutMapper.updateTie(tie);

        if (tie.getNextTieId() != null && !tie.isThirdPlace()) {
            KnockoutTie next = knockoutMapper.findTieById(tie.getNextTieId())
                    .orElseThrow(() -> notFound("下一轮对阵不存在"));
            if (fixtureMapper.findByKnockoutTieId(next.getId()).isPresent()) {
                throw conflict("下一轮比赛已存在，不能覆盖晋级结果");
            }
            if (tie.getNextSlot() == BracketSlot.HOME) {
                if (next.getHomeTeamId() != null && next.getHomeTeamId() != winnerId) throw conflict("下一轮主队位置已有球队");
                next.setHomeTeamId(winnerId);
            } else {
                if (next.getAwayTeamId() != null && next.getAwayTeamId() != winnerId) throw conflict("下一轮客队位置已有球队");
                next.setAwayTeamId(winnerId);
            }
            if (next.getHomeTeamId() != null && next.getAwayTeamId() != null) next.setStatus(TieStatus.READY);
            knockoutMapper.updateTie(next);
        }
        auditService.record(actorUserId, null, AuditAction.UPDATE, "KNOCKOUT_TIE", tieId, null,
                new TieResultSnapshot(tieId, homeScore, awayScore, homePenaltyScore, awayPenaltyScore, winnerId));
    }

    private void insertSeed(long stageId, Qualifier qualifier, long tieId, BracketSlot slot) {
        KnockoutSeed seed = new KnockoutSeed();
        seed.setStageId(stageId); seed.setSourceGroupId(qualifier.groupId()); seed.setSourceRank(qualifier.rank());
        seed.setTargetTieId(tieId); seed.setTargetSlot(slot); knockoutMapper.insertSeed(seed);
    }

    private BracketTieView toView(KnockoutTie tie) {
        Fixture fixture = fixtureMapper.findByKnockoutTieId(tie.getId()).orElse(null);
        return new BracketTieView(tie.getId(), tie.getBracketPosition(), teamRef(tie.getHomeTeamId()),
                teamRef(tie.getAwayTeamId()), teamRef(tie.getWinnerTeamId()),
                fixture == null ? null : fixture.getHomeScore(), fixture == null ? null : fixture.getAwayScore(),
                fixture == null ? null : fixture.getHomePenaltyScore(),
                fixture == null ? null : fixture.getAwayPenaltyScore(), tie.getStatus(),
                fixture == null ? null : fixture.getId(), tie.isThirdPlace());
    }

    private TeamRef teamRef(Long teamId) {
        if (teamId == null) return null;
        Team team = teamMapper.findById(teamId).orElse(null);
        return team == null ? null : new TeamRef(team.getId(), team.getName(), team.getShortName());
    }

    private Stage requireStage(long stageId, StageType type) {
        Stage stage = stageMapper.findById(stageId).orElseThrow(() -> notFound("阶段不存在"));
        if (stage.getType() != type) throw conflict("阶段类型不正确");
        return stage;
    }

    private String roundName(int roundNo, int roundCount) {
        int remaining = 1 << (roundCount - roundNo + 1);
        return switch (remaining) { case 2 -> "决赛"; case 4 -> "半决赛"; case 8 -> "1/4决赛"; case 16 -> "1/8决赛"; default -> "第" + roundNo + "轮"; };
    }

    private AppException conflict(String message) { return new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message); }
    private AppException notFound(String message) { return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message); }

    private record Qualifier(long groupId, int rank, long teamId) {}
    private record BracketSnapshot(long stageId, long groupStageId, int size, boolean thirdPlace) {}
    private record TieResultSnapshot(long tieId, int homeScore, int awayScore, Integer homePenaltyScore,
                                     Integer awayPenaltyScore, long winnerTeamId) {}

    public record TeamRef(long id, String name, String shortName) {}
    public record BracketTieView(long id, int bracketPosition, TeamRef homeTeam, TeamRef awayTeam,
                                 TeamRef winnerTeam, Integer homeScore, Integer awayScore,
                                 Integer homePenaltyScore, Integer awayPenaltyScore,
                                 TieStatus status, Long fixtureId, boolean thirdPlace) {}
    public record BracketRoundView(long id, int roundNo, String name, List<BracketTieView> ties) {}
    public record BracketView(long stageId, String stageName, List<BracketRoundView> rounds,
                              BracketTieView thirdPlace, TeamRef champion) {}
}
