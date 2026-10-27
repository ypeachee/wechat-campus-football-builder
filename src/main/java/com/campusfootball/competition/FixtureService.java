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
    private final AuthorizationService authorizationService;
    private final FixtureWriteGuard writeGuard;
    private final AuditService auditService;

    public FixtureService(FixtureMapper fixtureMapper, SeasonMapper seasonMapper,
                          AuthorizationService authorizationService, FixtureWriteGuard writeGuard,
                          AuditService auditService) {
        this.fixtureMapper = fixtureMapper;
        this.seasonMapper = seasonMapper;
        this.authorizationService = authorizationService;
        this.writeGuard = writeGuard;
        this.auditService = auditService;
    }

    public Fixture get(long fixtureId) {
        return fixtureMapper.findById(fixtureId).orElseThrow(this::notFound);
    }

    public List<Fixture> findPublicBySeason(long seasonId) {
        return fixtureMapper.findPublicBySeason(seasonId);
    }

    public List<Fixture> findAllBySeason(long seasonId) {
        return fixtureMapper.findAllBySeason(seasonId);
    }

    @Transactional
    public long createFixture(long actorUserId, long seasonId, Integer roundNo, long homeTeamId,
                              long awayTeamId, LocalDateTime kickoffAt, String venue) {
        authorizationService.requireAdmin(actorUserId);
        validateParticipants(seasonId, homeTeamId, awayTeamId);
        Fixture fixture = new Fixture();
        fixture.setSeasonId(seasonId);
        fixture.setRoundNo(roundNo);
        fixture.setHomeTeamId(homeTeamId);
        fixture.setAwayTeamId(awayTeamId);
        fixture.setKickoffAt(kickoffAt);
        fixture.setVenue(venue);
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
        authorizationService.requireAdmin(actorUserId);
        Fixture existing = get(fixtureId);
        boolean participantsChanged = existing.getSeasonId() != seasonId
                || existing.getHomeTeamId() != homeTeamId
                || existing.getAwayTeamId() != awayTeamId;
        if (participantsChanged
                && (fixtureMapper.countEvents(fixtureId) > 0 || fixtureMapper.countAppearances(fixtureId) > 0)) {
            throw new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "比赛已有数据，不能修改参赛球队或赛季");
        }
        validateParticipants(seasonId, homeTeamId, awayTeamId);
        fixtureMapper.updateSchedule(fixtureId, seasonId, roundNo, homeTeamId, awayTeamId, kickoffAt, venue);
        Fixture updated = get(fixtureId);
        auditService.record(actorUserId, null, AuditAction.UPDATE, "FIXTURE", fixtureId, existing, updated);
    }

    @Transactional
    public void changeStatus(long actorUserId, long fixtureId, FixtureStatus target) {
        authorizationService.requireAdmin(actorUserId);
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
        authorizationService.requireAdmin(actorUserId);
        Fixture existing = writeGuard.requireWritable(fixtureId);
        validateScore(homeScore, awayScore);
        fixtureMapper.updateScore(fixtureId, homeScore, awayScore);
        auditService.record(actorUserId, null, AuditAction.SCORE_UPDATE, "FIXTURE", fixtureId, existing, get(fixtureId));
    }

    @Transactional
    public void finish(long actorUserId, long fixtureId, int homeScore, int awayScore) {
        authorizationService.requireAdmin(actorUserId);
        Fixture existing = writeGuard.requireWritable(fixtureId);
        if (existing.getStatus() != FixtureStatus.LIVE) {
            throw conflict("只有进行中的比赛可以完赛");
        }
        validateScore(homeScore, awayScore);
        fixtureMapper.finish(fixtureId, homeScore, awayScore);
        auditService.record(actorUserId, null, AuditAction.FINISH, "FIXTURE", fixtureId, existing, get(fixtureId));
    }

    @Transactional
    public void setLocked(long actorUserId, long fixtureId, boolean locked) {
        authorizationService.requireAdmin(actorUserId);
        Fixture existing = get(fixtureId);
        fixtureMapper.updateLock(fixtureId, locked);
        auditService.record(actorUserId, null, locked ? AuditAction.LOCK : AuditAction.UNLOCK,
                "FIXTURE", fixtureId, existing, get(fixtureId));
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

    private void validateScore(int homeScore, int awayScore) {
        if (homeScore < 0 || awayScore < 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "比分不能为负数");
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
