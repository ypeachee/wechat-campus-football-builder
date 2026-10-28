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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AppearanceService {
    private final AppearanceMapper appearanceMapper;
    private final FixtureWriteGuard writeGuard;
    private final AuthorizationService authorizationService;
    private final PlayerMapper playerMapper;
    private final AuditService auditService;
    private final SuspensionService suspensionService;

    public AppearanceService(AppearanceMapper appearanceMapper, FixtureWriteGuard writeGuard,
                             AuthorizationService authorizationService, PlayerMapper playerMapper,
                             AuditService auditService, SuspensionService suspensionService) {
        this.appearanceMapper = appearanceMapper;
        this.writeGuard = writeGuard;
        this.authorizationService = authorizationService;
        this.playerMapper = playerMapper;
        this.auditService = auditService;
        this.suspensionService = suspensionService;
    }

    public java.util.List<FixtureAppearance> findByFixture(long fixtureId) {
        return appearanceMapper.findByFixtureId(fixtureId);
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
        suspensionService.assertPlayersEligible(fixtureId, teamId, java.util.List.of(playerId));
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
    public void replaceTeamLineup(long actorUserId, long fixtureId, long teamId, List<Long> starters,
                                  List<LineupSubstitute> substitutes) {
        Fixture fixture = writeGuard.requireWritable(fixtureId);
        validateWriteAccess(actorUserId, fixture, teamId);
        if (starters == null) starters = List.of();
        if (substitutes == null) substitutes = List.of();
        Set<Long> selected = new HashSet<>();
        for (Long playerId : starters) {
            if (playerId == null || !selected.add(playerId)) {
                throw conflict("首发名单中存在重复或无效球员");
            }
        }
        for (LineupSubstitute substitute : substitutes) {
            if (substitute == null || !selected.add(substitute.playerId())) {
                throw conflict("替补名单中存在重复或无效球员");
            }
            validateMinutes(substitute.minutesPlayed());
        }
        for (Long playerId : selected) {
            Player player = playerMapper.findById(playerId).orElseThrow(() -> notFound("球员不存在"));
            if (player.getTeamId() != teamId) {
                throw conflict("球员不属于该出场球队");
            }
        }
        for (Long eventPlayerId : appearanceMapper.findPlayerIdsWithEvents(fixtureId, teamId)) {
            if (!selected.contains(eventPlayerId)) {
                throw conflict("已有比赛事件的球员必须保留在阵容中");
            }
        }
        suspensionService.assertPlayersEligible(fixtureId, teamId, selected);

        List<FixtureAppearance> before = appearanceMapper.findByFixtureAndTeam(fixtureId, teamId);
        appearanceMapper.deleteByFixtureAndTeam(fixtureId, teamId);
        List<FixtureAppearance> after = new ArrayList<>();
        for (Long playerId : starters) {
            after.add(insertLineupAppearance(actorUserId, fixtureId, teamId, playerId, true, 90));
        }
        for (LineupSubstitute substitute : substitutes) {
            after.add(insertLineupAppearance(actorUserId, fixtureId, teamId, substitute.playerId(), false,
                    substitute.minutesPlayed()));
        }
        auditService.record(actorUserId, teamId, AuditAction.UPDATE, "TEAM_LINEUP", fixtureId,
                before, after);
    }

    private FixtureAppearance insertLineupAppearance(long actorUserId, long fixtureId, long teamId,
                                                     long playerId, boolean starter, Integer minutesPlayed) {
        FixtureAppearance appearance = new FixtureAppearance();
        appearance.setFixtureId(fixtureId);
        appearance.setTeamId(teamId);
        appearance.setPlayerId(playerId);
        appearance.setStarter(starter);
        appearance.setMinutesPlayed(minutesPlayed);
        appearance.setCreatedBy(actorUserId);
        appearanceMapper.insert(appearance);
        return appearance;
    }

    public record LineupSubstitute(long playerId, Integer minutesPlayed) {
    }

    @Transactional
    public void update(long actorUserId, long appearanceId, boolean starter, Integer minutesPlayed) {
        FixtureAppearance existing = get(appearanceId);
        Fixture fixture = writeGuard.requireWritable(existing.getFixtureId());
        validateWriteAccess(actorUserId, fixture, existing.getTeamId());
        validateMinutes(minutesPlayed);
        suspensionService.assertPlayersEligible(existing.getFixtureId(), existing.getTeamId(),
                java.util.List.of(existing.getPlayerId()));
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
        if (authorizationService.isManagementAdmin(actorUserId)) {
            if (fixture.getStatus() != FixtureStatus.SCHEDULED
                    && fixture.getStatus() != FixtureStatus.LIVE
                    && fixture.getStatus() != FixtureStatus.FINISHED) {
                throw forbidden("当前比赛状态不允许维护出场记录");
            }
            return;
        }
        authorizationService.requireCanManageTeam(actorUserId, teamId);
        if (fixture.getStatus() != FixtureStatus.SCHEDULED
                && fixture.getStatus() != FixtureStatus.LIVE
                && fixture.getStatus() != FixtureStatus.FINISHED) {
            throw forbidden("领队只能维护未开始、进行中或已完赛比赛的本队出场");
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
