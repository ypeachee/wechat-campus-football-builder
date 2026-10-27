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
public class EventService {
    private final EventMapper eventMapper;
    private final FixtureWriteGuard writeGuard;
    private final AuthorizationService authorizationService;
    private final PlayerMapper playerMapper;
    private final AuditService auditService;

    public EventService(EventMapper eventMapper, FixtureWriteGuard writeGuard,
                        AuthorizationService authorizationService, PlayerMapper playerMapper,
                        AuditService auditService) {
        this.eventMapper = eventMapper;
        this.writeGuard = writeGuard;
        this.authorizationService = authorizationService;
        this.playerMapper = playerMapper;
        this.auditService = auditService;
    }

    @Transactional
    public long create(long actorUserId, long fixtureId, long teamId, long playerId, EventType eventType,
                       Long relatedPlayerId, int minute, int stoppageMinute, String note) {
        Fixture fixture = writeGuard.requireWritable(fixtureId);
        validateWriteAccess(actorUserId, fixture, teamId);
        validateMinute(minute, stoppageMinute);
        Player player = playerMapper.findById(playerId).orElseThrow(() -> notFound("球员不存在"));
        validateEventRules(fixture, teamId, player, eventType, relatedPlayerId);

        FixtureEvent event = new FixtureEvent();
        event.setFixtureId(fixtureId);
        event.setTeamId(teamId);
        event.setPlayerId(playerId);
        event.setEventType(eventType);
        event.setRelatedPlayerId(relatedPlayerId);
        event.setMinute(minute);
        event.setStoppageMinute(stoppageMinute);
        event.setNote(note);
        event.setCreatedBy(actorUserId);
        eventMapper.insert(event);
        auditService.record(actorUserId, teamId, AuditAction.CREATE, "EVENT", event.getId(), null, event);
        return event.getId();
    }

    @Transactional
    public void update(long actorUserId, long eventId, long playerId, EventType eventType,
                       Long relatedPlayerId, int minute, int stoppageMinute, String note) {
        FixtureEvent existing = get(eventId);
        Fixture fixture = writeGuard.requireWritable(existing.getFixtureId());
        validateWriteAccess(actorUserId, fixture, existing.getTeamId());
        validateMinute(minute, stoppageMinute);
        Player player = playerMapper.findById(playerId).orElseThrow(() -> notFound("球员不存在"));
        validateEventRules(fixture, existing.getTeamId(), player, eventType, relatedPlayerId);
        eventMapper.update(eventId, playerId, eventType, relatedPlayerId, minute, stoppageMinute, note);
        auditService.record(actorUserId, existing.getTeamId(), AuditAction.UPDATE, "EVENT", eventId,
                existing, get(eventId));
    }

    @Transactional
    public void delete(long actorUserId, long eventId) {
        FixtureEvent existing = get(eventId);
        Fixture fixture = writeGuard.requireWritable(existing.getFixtureId());
        validateWriteAccess(actorUserId, fixture, existing.getTeamId());
        eventMapper.delete(eventId);
        auditService.record(actorUserId, existing.getTeamId(), AuditAction.DELETE, "EVENT", eventId, existing, null);
    }

    private FixtureEvent get(long eventId) {
        return eventMapper.findById(eventId).orElseThrow(() -> notFound("比赛事件不存在"));
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
            throw forbidden("当前比赛状态不允许维护比赛事件");
        }
    }

    private void validateEventRules(Fixture fixture, long teamId, Player player, EventType eventType,
                                    Long relatedPlayerId) {
        if (eventType == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "事件类型不能为空");
        }
        long expectedPlayerTeam = eventType == EventType.OWN_GOAL ? opponentTeam(fixture, teamId) : teamId;
        if (player.getTeamId() != expectedPlayerTeam) {
            throw conflict(eventType == EventType.OWN_GOAL ? "乌龙球球员必须属于对方球队" : "球员不属于事件球队");
        }
        if (eventType != EventType.GOAL && relatedPlayerId != null) {
            throw conflict("只有进球事件可以记录助攻球员");
        }
        if (relatedPlayerId != null) {
            if (relatedPlayerId != null && relatedPlayerId.longValue() == player.getId().longValue()) {
                throw conflict("球员不能助攻自己");
            }
            Player assister = playerMapper.findById(relatedPlayerId)
                    .orElseThrow(() -> notFound("助攻球员不存在"));
            if (assister.getTeamId() != teamId) {
                throw conflict("助攻球员必须与进球球员同队");
            }
        }
    }

    private long opponentTeam(Fixture fixture, long teamId) {
        return fixture.getHomeTeamId() == teamId ? fixture.getAwayTeamId() : fixture.getHomeTeamId();
    }

    private void validateMinute(int minute, int stoppageMinute) {
        if (minute < 1 || minute > 130 || stoppageMinute < 0 || stoppageMinute > 30) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "比赛时间不合法");
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

