package com.campusfootball.competition;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.team.AuthorizationService;
import com.campusfootball.team.Player;
import com.campusfootball.team.PlayerMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppearanceService {
    private final AppearanceMapper appearanceMapper;
    private final FixtureWriteGuard writeGuard;
    private final AuthorizationService authorizationService;
    private final PlayerMapper playerMapper;
    private final AuditService auditService;

    public AppearanceService(AppearanceMapper appearanceMapper, FixtureWriteGuard writeGuard,
                             AuthorizationService authorizationService, PlayerMapper playerMapper,
                             AuditService auditService) {
        this.appearanceMapper = appearanceMapper;
        this.writeGuard = writeGuard;
        this.authorizationService = authorizationService;
        this.playerMapper = playerMapper;
        this.auditService = auditService;
    }

    @Transactional
    public long create(long actorUserId, long fixtureId, long teamId, long playerId,
                       boolean starter, Integer minutesPlayed) {
        Fixture fixture = writeGuard.requireWritable(fixtureId);
        validateWriteAccess(actorUserId, fixture, teamId);
        validateMinutes(minutesPlayed);
        Player player = playerMapper.findById(playerId)
                .orElseThrow(() -> notFound("球员不存在"));
        if (player.getTeamId() != teamId) {
            throw conflict("球员不属于该出场球队");
        }
        if (appearanceMapper.countByFixtureAndPlayer(fixtureId, playerId) > 0) {
            throw conflict("该球员已有出场记录");
        }
        FixtureAppearance appearance = new FixtureAppearance();
        appearance.setFixtureId(fixtureId);
        appearance.setTeamId(teamId);
        appearance.setPlayerId(playerId);
        appearance.setStarter(starter);
        appearance.setMinutesPlayed(minutesPlayed);
        appearance.setCreatedBy(actorUserId);
        appearanceMapper.insert(appearance);
        auditService.record(actorUserId, teamId, AuditAction.CREATE, "APPEARANCE", appearance.getId(), null, appearance);
        return appearance.getId();
    }

    @Transactional
    public void update(long actorUserId, long appearanceId, boolean starter, Integer minutesPlayed) {
        FixtureAppearance existing = get(appearanceId);
        Fixture fixture = writeGuard.requireWritable(existing.getFixtureId());
        validateWriteAccess(actorUserId, fixture, existing.getTeamId());
        validateMinutes(minutesPlayed);
        appearanceMapper.update(appearanceId, starter, minutesPlayed);
        FixtureAppearance updated = get(appearanceId);
        auditService.record(actorUserId, existing.getTeamId(), AuditAction.UPDATE, "APPEARANCE",
                appearanceId, existing, updated);
    }

    @Transactional
    public void delete(long actorUserId, long appearanceId) {
        FixtureAppearance existing = get(appearanceId);
        Fixture fixture = writeGuard.requireWritable(existing.getFixtureId());
        validateWriteAccess(actorUserId, fixture, existing.getTeamId());
        appearanceMapper.delete(appearanceId);
        auditService.record(actorUserId, existing.getTeamId(), AuditAction.DELETE, "APPEARANCE",
                appearanceId, existing, null);
    }

    private FixtureAppearance get(long appearanceId) {
        return appearanceMapper.findById(appearanceId).orElseThrow(() -> notFound("出场记录不存在"));
    }

    private void validateWriteAccess(long actorUserId, Fixture fixture, long teamId) {
        if (teamId != fixture.getHomeTeamId() && teamId != fixture.getAwayTeamId()) {
            throw conflict("球队不是本场比赛参赛球队");
        }
        boolean admin = authorizationService.isAdmin(actorUserId);
        if (!admin) {
            authorizationService.requireCanManageTeam(actorUserId, teamId);
            if (fixture.getStatus() != FixtureStatus.LIVE && fixture.getStatus() != FixtureStatus.FINISHED) {
                throw forbidden("球队管理员只能维护进行中或已完赛比赛的数据");
            }
            return;
        }
        if (fixture.getStatus() != FixtureStatus.SCHEDULED
                && fixture.getStatus() != FixtureStatus.LIVE
                && fixture.getStatus() != FixtureStatus.FINISHED) {
            throw forbidden("当前比赛状态不允许维护出场记录");
        }
    }

    private void validateMinutes(Integer minutesPlayed) {
        if (minutesPlayed != null && (minutesPlayed < 0 || minutesPlayed > 130)) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "出场分钟数必须在 0 到 130 之间");
        }
    }

    private AppException conflict(String message) {
        return new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }

    private AppException forbidden(String message) {
        return new AppException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, message);
    }

    private AppException notFound(String message) {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }
}
