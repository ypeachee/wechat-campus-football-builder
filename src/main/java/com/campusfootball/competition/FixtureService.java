package com.campusfootball.competition;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.team.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FixtureService {
    private final FixtureMapper fixtureMapper;
    private final SeasonMapper seasonMapper;
    private final StageMapper stageMapper;
    private final GroupMapper groupMapper;
    private final AuthorizationService authorizationService;
    private final FixtureWriteGuard writeGuard;
    private final AuditService auditService;
    private final BracketService bracketService;

    public FixtureService(FixtureMapper fixtureMapper, SeasonMapper seasonMapper, StageMapper stageMapper,
                          GroupMapper groupMapper, AuthorizationService authorizationService,
                          FixtureWriteGuard writeGuard, AuditService auditService, BracketService bracketService) {
        this.fixtureMapper = fixtureMapper;
        this.seasonMapper = seasonMapper;
        this.stageMapper = stageMapper;
        this.groupMapper = groupMapper;
        this.authorizationService = authorizationService;
        this.writeGuard = writeGuard;
        this.auditService = auditService;
        this.bracketService = bracketService;
    }

    public Fixture get(long fixtureId) {
        return fixtureMapper.findById(fixtureId).orElseThrow(this::notFound);
    }

    public Fixture getPublic(long fixtureId) {
        Fixture fixture = get(fixtureId);
        if (fixture.getStatus() == FixtureStatus.DRAFT) {
            throw notFound();
        }
        return fixture;
    }

    public List<Fixture> findPublicBySeason(long seasonId) {
        return fixtureMapper.findPublicBySeason(seasonId);
    }

    public List<Fixture> findAllBySeason(long seasonId) {
        return fixtureMapper.findAllBySeason(seasonId);
    }

    public List<Fixture> findPublicByStage(long stageId, Long groupId) {
        return fixtureMapper.findPublicByStage(stageId, groupId);
    }

    public List<Fixture> findAllByStage(long stageId, Long groupId) {
        return fixtureMapper.findAllByStage(stageId, groupId);
    }

    @Transactional
    public long createFixture(long actorUserId, long seasonId, Integer roundNo, long homeTeamId,
                              long awayTeamId, LocalDateTime kickoffAt, String venue) {
        return createFixture(actorUserId, seasonId, null, null, roundNo, homeTeamId, awayTeamId, kickoffAt, venue, null);
    }

    @Transactional
    public long createFixture(long actorUserId, long seasonId, Long stageId, Long groupId, Integer roundNo,
                              long homeTeamId, long awayTeamId, LocalDateTime kickoffAt, String venue,
                              String referee) {
        authorizationService.requireManagementAccess(actorUserId);
        validateParticipants(seasonId, homeTeamId, awayTeamId);
        Stage stage = resolveStage(seasonId, stageId);
        if (stage.getType() == StageType.KNOCKOUT) {
            throw conflict("淘汰赛比赛必须从对阵节点创建");
        }
        validateGroupPlacement(stage, groupId, homeTeamId, awayTeamId);
        Fixture fixture = new Fixture();
        fixture.setSeasonId(seasonId);
        fixture.setStageId(stage.getId());
        fixture.setGroupId(groupId);
        fixture.setRoundNo(roundNo);
        fixture.setHomeTeamId(homeTeamId);
        fixture.setAwayTeamId(awayTeamId);
        fixture.setKickoffAt(kickoffAt);
        fixture.setVenue(venue);
        fixture.setReferee(normalizeReferee(referee));
        fixture.setStatus(FixtureStatus.DRAFT);
        fixture.setDataLocked(false);
        fixture.setCreatedBy(actorUserId);
        fixtureMapper.insert(fixture);
        auditService.record(actorUserId, null, AuditAction.CREATE, "FIXTURE", fixture.getId(), null, fixture);
        return fixture.getId();
    }

    @Transactional
    public void updateSchedule(long actorUserId, long fixtureId, long seasonId, Integer roundNo,
                               long homeTeamId, long awayTeamId, LocalDateTime kickoffAt, String venue) {
        updateSchedule(actorUserId, fixtureId, seasonId, null, null, roundNo, homeTeamId, awayTeamId,
                kickoffAt, venue, null);
    }

    @Transactional
    public void updateSchedule(long actorUserId, long fixtureId, long seasonId, Long stageId, Long groupId,
                               Integer roundNo, long homeTeamId, long awayTeamId,
                               LocalDateTime kickoffAt, String venue, String referee) {
        authorizationService.requireManagementAccess(actorUserId);
        Fixture existing = get(fixtureId);
        boolean participantsChanged = existing.getSeasonId() != seasonId
                || existing.getHomeTeamId() != homeTeamId
                || existing.getAwayTeamId() != awayTeamId;
        if (participantsChanged
                && (fixtureMapper.countEvents(fixtureId) > 0 || fixtureMapper.countAppearances(fixtureId) > 0)) {
            throw new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "比赛已有数据，不能修改参赛球队或赛季");
        }
        validateParticipants(seasonId, homeTeamId, awayTeamId);
        Stage stage = resolveStage(seasonId, stageId);
        if (stage.getType() == StageType.KNOCKOUT) {
            throw conflict("淘汰赛比赛必须通过对阵节点维护");
        }
        validateGroupPlacement(stage, groupId, homeTeamId, awayTeamId);
        fixtureMapper.updateSchedule(fixtureId, seasonId, stage.getId(), groupId, roundNo,
                homeTeamId, awayTeamId, kickoffAt, venue, normalizeReferee(referee));
        Fixture updated = get(fixtureId);
        auditService.record(actorUserId, null, AuditAction.UPDATE, "FIXTURE", fixtureId, existing, updated);
    }

    @Transactional
    public void changeStatus(long actorUserId, long fixtureId, FixtureStatus target) {
        authorizationService.requireManagementAccess(actorUserId);
        Fixture existing = get(fixtureId);
        if (target == FixtureStatus.FINISHED) {
            throw conflict("完赛必须使用完赛接口");
        }
        if (!isAllowedTransition(existing.getStatus(), target)) {
            throw conflict("不允许的比赛状态流转");
        }
        fixtureMapper.updateStatus(fixtureId, target);
        auditService.record(actorUserId, null, AuditAction.UPDATE, "FIXTURE", fixtureId, existing, get(fixtureId));
    }

    @Transactional
    public void updateScore(long actorUserId, long fixtureId, int homeScore, int awayScore) {
        updateScore(actorUserId, fixtureId, homeScore, awayScore, null, null);
    }

    @Transactional
    public void updateScore(long actorUserId, long fixtureId, int homeScore, int awayScore,
                            Integer homePenaltyScore, Integer awayPenaltyScore) {
        authorizationService.requireManagementAccess(actorUserId);
        Fixture existing = writeGuard.requireWritable(fixtureId);
        validateScore(homeScore, awayScore);
        validatePenalties(existing, homePenaltyScore, awayPenaltyScore);
        fixtureMapper.updateScore(fixtureId, homeScore, awayScore, homePenaltyScore, awayPenaltyScore);
        auditService.record(actorUserId, null, AuditAction.SCORE_UPDATE, "FIXTURE", fixtureId, existing, get(fixtureId));
    }

    @Transactional
    public void finish(long actorUserId, long fixtureId, int homeScore, int awayScore) {
        finish(actorUserId, fixtureId, homeScore, awayScore, null, null);
    }

    @Transactional
    public void finish(long actorUserId, long fixtureId, int homeScore, int awayScore,
                       Integer homePenaltyScore, Integer awayPenaltyScore) {
        authorizationService.requireManagementAccess(actorUserId);
        Fixture existing = writeGuard.requireWritable(fixtureId);
        if (existing.getStatus() != FixtureStatus.LIVE) {
            throw conflict("只有进行中的比赛可以完赛");
        }
        validateScore(homeScore, awayScore);
        validatePenalties(existing, homePenaltyScore, awayPenaltyScore);
        if (existing.getKnockoutTieId() != null && homeScore == awayScore
                && (homePenaltyScore == null || homePenaltyScore.equals(awayPenaltyScore))) {
            throw conflict("淘汰赛平局必须提供不同的点球比分");
        }
        fixtureMapper.finish(fixtureId, homeScore, awayScore, homePenaltyScore, awayPenaltyScore);
        if (existing.getKnockoutTieId() != null) {
            bracketService.finishTie(actorUserId, existing.getKnockoutTieId(), homeScore, awayScore,
                    homePenaltyScore, awayPenaltyScore);
        }
        auditService.record(actorUserId, null, AuditAction.FINISH, "FIXTURE", fixtureId, existing, get(fixtureId));
    }

    @Transactional
    public void setLocked(long actorUserId, long fixtureId, boolean locked) {
        authorizationService.requireManagementAccess(actorUserId);
        Fixture existing = get(fixtureId);
        fixtureMapper.updateLock(fixtureId, locked);
        auditService.record(actorUserId, null, locked ? AuditAction.LOCK : AuditAction.UNLOCK,
                "FIXTURE", fixtureId, existing, get(fixtureId));
    }

    private String normalizeReferee(String referee) {
        return referee == null || referee.isBlank() ? null : referee.trim();
    }

    private void validateParticipants(long seasonId, long homeTeamId, long awayTeamId) {
        if (homeTeamId == awayTeamId) {
            throw conflict("主队和客队不能相同");
        }
        seasonMapper.findById(seasonId).orElseThrow(() -> notFound("赛季不存在"));
        if (seasonMapper.countSeasonTeam(seasonId, homeTeamId) == 0
                || seasonMapper.countSeasonTeam(seasonId, awayTeamId) == 0) {
            throw conflict("比赛球队必须属于该赛季");
        }
    }

    private Stage resolveStage(long seasonId, Long stageId) {
        if (stageId == null) {
            return stageMapper.findFirstLeagueStage(seasonId)
                    .orElseThrow(() -> conflict("赛季没有可用联赛阶段，请显式指定阶段"));
        }
        Stage stage = stageMapper.findById(stageId).orElseThrow(() -> notFound("阶段不存在"));
        if (stage.getSeasonId() != seasonId) {
            throw conflict("阶段不属于该赛季");
        }
        return stage;
    }

    private void validateGroupPlacement(Stage stage, Long groupId, long homeTeamId, long awayTeamId) {
        if (stage.getType() == StageType.GROUP) {
            if (groupId == null) {
                throw conflict("小组赛比赛必须指定小组");
            }
            CompetitionGroup group = groupMapper.findById(groupId).orElseThrow(() -> notFound("小组不存在"));
            if (!group.getStageId().equals(stage.getId())) {
                throw conflict("小组不属于该阶段");
            }
            if (groupMapper.countGroupTeam(groupId, homeTeamId) == 0
                    || groupMapper.countGroupTeam(groupId, awayTeamId) == 0) {
                throw conflict("比赛球队必须属于该小组");
            }
        } else if (stage.getType() == StageType.LEAGUE && groupId != null) {
            throw conflict("联赛阶段比赛不能指定小组");
        }
    }

    private void validateScore(int homeScore, int awayScore) {
        if (homeScore < 0 || awayScore < 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "比分不能为负数");
        }
    }

    private void validatePenalties(Fixture fixture, Integer homePenaltyScore, Integer awayPenaltyScore) {
        if ((homePenaltyScore == null) != (awayPenaltyScore == null)) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "点球比分必须同时填写");
        }
        if (homePenaltyScore != null && (homePenaltyScore < 0 || awayPenaltyScore < 0)) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "点球比分不能为负数");
        }
        if (fixture.getKnockoutTieId() == null && homePenaltyScore != null) {
            throw conflict("只有淘汰赛可以记录点球比分");
        }
    }

    private boolean isAllowedTransition(FixtureStatus from, FixtureStatus to) {
        return switch (from) {
            case DRAFT -> to == FixtureStatus.SCHEDULED || to == FixtureStatus.CANCELLED;
            case SCHEDULED -> to == FixtureStatus.LIVE || to == FixtureStatus.CANCELLED;
            case LIVE -> to == FixtureStatus.CANCELLED;
            case FINISHED, CANCELLED -> false;
        };
    }

    private AppException conflict(String message) {
        return new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }

    private AppException notFound() {
        return notFound("比赛不存在");
    }

    private AppException notFound(String message) {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }
}
