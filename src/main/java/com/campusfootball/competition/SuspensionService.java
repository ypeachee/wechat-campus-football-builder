package com.campusfootball.competition;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.team.AuthorizationService;
import com.campusfootball.team.Player;
import com.campusfootball.team.PlayerMapper;
import com.campusfootball.team.Team;
import com.campusfootball.team.TeamMapper;
import com.campusfootball.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class SuspensionService {
    private final SuspensionMapper suspensionMapper;
    private final FixtureMapper fixtureMapper;
    private final AppearanceMapper appearanceMapper;
    private final PlayerMapper playerMapper;
    private final TeamMapper teamMapper;
    private final SeasonMapper seasonMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public SuspensionService(SuspensionMapper suspensionMapper, FixtureMapper fixtureMapper,
                             AppearanceMapper appearanceMapper, PlayerMapper playerMapper,
                             TeamMapper teamMapper, SeasonMapper seasonMapper,
                             AuthorizationService authorizationService, AuditService auditService) {
        this.suspensionMapper = suspensionMapper;
        this.fixtureMapper = fixtureMapper;
        this.appearanceMapper = appearanceMapper;
        this.playerMapper = playerMapper;
        this.teamMapper = teamMapper;
        this.seasonMapper = seasonMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @Transactional
    public void recalculateSeason(long seasonId) {
        List<CardEventRow> cards = suspensionMapper.findFinishedCardEventsBySeason(seasonId);
        Map<Long, Integer> yellowCounts = new HashMap<>();
        Set<SourceKey> expected = new HashSet<>();
        for (CardEventRow card : cards) {
            if (card.getEventType() == EventType.RED_CARD) {
                ensureAutomatic(card, SuspensionSourceType.RED_CARD, "直接红牌停赛1场", expected);
            } else if (card.getEventType() == EventType.YELLOW_CARD) {
                int count = yellowCounts.merge(card.getPlayerId(), 1, Integer::sum);
                if (count % 3 == 0) {
                    ensureAutomatic(card, SuspensionSourceType.YELLOW_ACCUMULATION,
                            "赛季累计3张黄牌停赛1场", expected);
                }
            }
        }

        for (PlayerSuspension existing : suspensionMapper.findAutomaticBySeason(seasonId)) {
            SourceKey key = new SourceKey(existing.getSourceType(), existing.getSourceEventId());
            if (!expected.contains(key) && existing.getMatchesServed() == 0) {
                suspensionMapper.delete(existing.getId());
            }
        }
    }

    private void ensureAutomatic(CardEventRow card, SuspensionSourceType type, String reason,
                                 Set<SourceKey> expected) {
        SourceKey key = new SourceKey(type, card.getEventId());
        expected.add(key);
        boolean alreadyExists = suspensionMapper.findBySourceEventId(card.getEventId()).stream()
                .anyMatch(item -> item.getSourceType() == type);
        if (alreadyExists) {
            return;
        }
        PlayerSuspension suspension = new PlayerSuspension();
        suspension.setPlayerId(card.getPlayerId());
        suspension.setTeamId(card.getTeamId());
        suspension.setSeasonId(fixtureMapper.findById(card.getFixtureId()).orElseThrow().getSeasonId());
        suspension.setSourceType(type);
        suspension.setSourceEventId(card.getEventId());
        suspension.setSourceFixtureId(card.getFixtureId());
        suspension.setReason(reason);
        suspension.setMatchesTotal(1);
        suspension.setMatchesServed(0);
        suspension.setStatus(SuspensionStatus.ACTIVE);
        suspension.setCreatedBy(card.getEventCreatedBy());
        suspensionMapper.insert(suspension);
    }

    public void assertCardEventMutable(long eventId) {
        for (PlayerSuspension suspension : suspensionMapper.findBySourceEventId(eventId)) {
            if (suspension.getMatchesServed() != null && suspension.getMatchesServed() > 0) {
                throw conflict("该红黄牌已经执行停赛，不能修改或删除");
            }
        }
    }

    @Transactional
    public void prepareCardEventDelete(long eventId) {
        List<PlayerSuspension> suspensions = suspensionMapper.findBySourceEventId(eventId);
        for (PlayerSuspension suspension : suspensions) {
            if (suspension.getMatchesServed() != null && suspension.getMatchesServed() > 0) {
                throw conflict("该红黄牌已经执行停赛，不能删除");
            }
        }
        for (PlayerSuspension suspension : suspensions) {
            if (suspension.getSourceType() != SuspensionSourceType.MANUAL) {
                suspensionMapper.delete(suspension.getId());
            }
        }
    }

    @Transactional
    public void serveAfterFinish(Fixture fixture) {
        if (fixture.getStatus() != FixtureStatus.FINISHED) {
            return;
        }
        serveTeam(fixture, fixture.getHomeTeamId());
        serveTeam(fixture, fixture.getAwayTeamId());
    }

    private void serveTeam(Fixture fixture, long teamId) {
        Map<Long, List<PlayerSuspension>> byPlayer = new HashMap<>();
        for (PlayerSuspension suspension : suspensionMapper.findBySeasonAndTeam(fixture.getSeasonId(), teamId)) {
            if (suspension.getStatus() == SuspensionStatus.ACTIVE
                    && remaining(suspension) > 0
                    && appliesToFixture(suspension, fixture)) {
                byPlayer.computeIfAbsent(suspension.getPlayerId(), ignored -> new ArrayList<>()).add(suspension);
            }
        }
        for (Map.Entry<Long, List<PlayerSuspension>> entry : byPlayer.entrySet()) {
            long playerId = entry.getKey();
            if (appearanceMapper.countByFixtureAndPlayer(fixture.getId(), playerId) > 0) {
                continue;
            }
            PlayerSuspension next = entry.getValue().stream()
                    .sorted(Comparator.comparing(PlayerSuspension::getCreatedAt,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(PlayerSuspension::getId))
                    .findFirst().orElse(null);
            if (next == null || suspensionMapper.countServing(next.getId(), fixture.getId()) > 0) {
                continue;
            }
            int served = next.getMatchesServed() + 1;
            SuspensionStatus status = served >= next.getMatchesTotal()
                    ? SuspensionStatus.SERVED : SuspensionStatus.ACTIVE;
            suspensionMapper.insertServing(next.getId(), fixture.getId());
            suspensionMapper.updateProgress(next.getId(), served, status);
        }
    }

    public void assertPlayersEligible(long fixtureId, long teamId, Collection<Long> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return;
        }
        Fixture fixture = fixtureMapper.findById(fixtureId).orElseThrow(this::fixtureNotFound);
        Set<Long> selected = new HashSet<>(playerIds);
        for (PlayerSuspension suspension : applicableActive(fixture, teamId)) {
            if (selected.contains(suspension.getPlayerId())) {
                Player player = playerMapper.findById(suspension.getPlayerId()).orElse(null);
                String name = player == null ? "该球员" : player.getName();
                throw conflict(name + "处于禁赛期，剩余" + remaining(suspension) + "场");
            }
        }
    }

    public List<SuspensionView> findByTeam(long actorUserId, long seasonId, long teamId) {
        authorizationService.requireCanManageTeam(actorUserId, teamId);
        seasonMapper.findById(seasonId).orElseThrow(() -> notFound("赛季不存在"));
        return suspensionMapper.findBySeasonAndTeam(seasonId, teamId).stream()
                .map(this::toView).toList();
    }

    public List<SuspensionView> findByFixture(long actorUserId, long fixtureId) {
        Fixture fixture = fixtureMapper.findById(fixtureId).orElseThrow(this::fixtureNotFound);
        UserRole role = authorizationService.currentRole(actorUserId);
        List<Long> teamIds = new ArrayList<>();
        if (role == UserRole.ADMIN || role == UserRole.TEAM_MANAGER) {
            teamIds.add(fixture.getHomeTeamId());
            teamIds.add(fixture.getAwayTeamId());
        } else if (role == UserRole.TEAM_LEADER) {
            for (long teamId : List.of(fixture.getHomeTeamId(), fixture.getAwayTeamId())) {
                List<Long> managed = authorizationService.managedTeamIds(actorUserId);
                if (managed.contains(teamId)) {
                    teamIds.add(teamId);
                }
            }
            if (teamIds.isEmpty()) throw forbidden();
        } else {
            throw forbidden();
        }
        List<SuspensionView> views = new ArrayList<>();
        for (long teamId : teamIds) {
            for (PlayerSuspension suspension : applicableActive(fixture, teamId)) {
                views.add(toView(suspension));
            }
        }
        views.sort(Comparator.comparing(SuspensionView::teamId)
                .thenComparing(SuspensionView::playerName));
        return views;
    }

    public List<SuspensionView> findActiveByTeams(long seasonId, long homeTeamId, long awayTeamId) {
        List<SuspensionView> views = new ArrayList<>();
        for (long teamId : List.of(homeTeamId, awayTeamId)) {
            suspensionMapper.findBySeasonAndTeam(seasonId, teamId).stream()
                    .filter(item -> item.getStatus() == SuspensionStatus.ACTIVE && remaining(item) > 0)
                    .map(this::toView).forEach(views::add);
        }
        return views;
    }

    @Transactional
    public long createManual(long actorUserId, long playerId, long seasonId, int matchesTotal, String reason) {
        authorizationService.requireManagementAccess(actorUserId);
        validateMatches(matchesTotal, 0);
        seasonMapper.findById(seasonId).orElseThrow(() -> notFound("赛季不存在"));
        Player player = playerMapper.findById(playerId).orElseThrow(() -> notFound("球员不存在"));
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isBlank() || normalizedReason.length() > 255) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "请填写255字以内的禁赛原因");
        }
        PlayerSuspension suspension = new PlayerSuspension();
        suspension.setPlayerId(playerId);
        suspension.setTeamId(player.getTeamId());
        suspension.setSeasonId(seasonId);
        suspension.setSourceType(SuspensionSourceType.MANUAL);
        suspension.setReason(normalizedReason);
        suspension.setMatchesTotal(matchesTotal);
        suspension.setMatchesServed(0);
        suspension.setStatus(SuspensionStatus.ACTIVE);
        suspension.setCreatedBy(actorUserId);
        suspensionMapper.insert(suspension);
        auditService.record(actorUserId, player.getTeamId(), AuditAction.CREATE, "SUSPENSION",
                suspension.getId(), null, suspension);
        return suspension.getId();
    }

    @Transactional
    public void updateManual(long actorUserId, long suspensionId, int matchesTotal, SuspensionStatus status) {
        authorizationService.requireManagementAccess(actorUserId);
        PlayerSuspension existing = suspensionMapper.findById(suspensionId)
                .orElseThrow(() -> notFound("禁赛记录不存在"));
        validateMatches(matchesTotal, existing.getMatchesServed());
        if (status != SuspensionStatus.ACTIVE && status != SuspensionStatus.CANCELLED) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "禁赛状态不合法");
        }
        SuspensionStatus nextStatus = status;
        if (status == SuspensionStatus.ACTIVE && existing.getMatchesServed() >= matchesTotal) {
            nextStatus = SuspensionStatus.SERVED;
        }
        suspensionMapper.updateTotalAndStatus(suspensionId, matchesTotal, nextStatus);
        PlayerSuspension updated = suspensionMapper.findById(suspensionId).orElseThrow();
        auditService.record(actorUserId, existing.getTeamId(), AuditAction.UPDATE, "SUSPENSION",
                suspensionId, existing, updated);
    }

    private List<PlayerSuspension> applicableActive(Fixture fixture, long teamId) {
        return suspensionMapper.findBySeasonAndTeam(fixture.getSeasonId(), teamId).stream()
                .filter(item -> item.getStatus() == SuspensionStatus.ACTIVE && remaining(item) > 0)
                .filter(item -> appliesToFixture(item, fixture))
                .toList();
    }

    private boolean appliesToFixture(PlayerSuspension suspension, Fixture fixture) {
        if (suspension.getSourceFixtureId() == null) {
            return true;
        }
        if (fixture.getId().equals(suspension.getSourceFixtureId())) {
            return false;
        }
        Fixture source = fixtureMapper.findById(suspension.getSourceFixtureId()).orElse(null);
        if (source == null) {
            return fixture.getId() > suspension.getSourceFixtureId();
        }
        LocalDateTime sourceKickoff = source.getKickoffAt();
        LocalDateTime fixtureKickoff = fixture.getKickoffAt();
        if (sourceKickoff != null && fixtureKickoff != null) {
            return fixtureKickoff.isAfter(sourceKickoff)
                    || (fixtureKickoff.isEqual(sourceKickoff) && fixture.getId() > source.getId());
        }
        return fixture.getId() > source.getId();
    }

    private int remaining(PlayerSuspension suspension) {
        return suspension.getMatchesTotal() - suspension.getMatchesServed();
    }

    private void validateMatches(int matchesTotal, int matchesServed) {
        if (matchesTotal < 1 || matchesTotal > 20 || matchesServed < 0 || matchesServed > matchesTotal) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "停赛场次必须在1到20场之间");
        }
    }

    private SuspensionView toView(PlayerSuspension suspension) {
        Player player = playerMapper.findById(suspension.getPlayerId()).orElse(null);
        Team team = teamMapper.findById(suspension.getTeamId()).orElse(null);
        int remaining = Math.max(0, suspension.getMatchesTotal() - suspension.getMatchesServed());
        return new SuspensionView(suspension.getId(), suspension.getPlayerId(),
                player == null ? null : player.getJerseyNo(), player == null ? "未知球员" : player.getName(),
                suspension.getTeamId(), team == null ? "未知球队" : team.getName(),
                suspension.getSeasonId(), suspension.getSourceType(), suspension.getSourceFixtureId(),
                suspension.getReason(), suspension.getMatchesTotal(), suspension.getMatchesServed(), remaining,
                suspension.getStatus(), suspension.getCreatedAt());
    }

    private AppException conflict(String message) {
        return new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }

    private AppException forbidden() {
        return new AppException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "没有权限执行该操作");
    }

    private AppException notFound(String message) {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    private AppException fixtureNotFound() {
        return notFound("比赛不存在");
    }

    private record SourceKey(SuspensionSourceType type, Long eventId) {
    }

    public record SuspensionView(long id, long playerId, Integer jerseyNo, String playerName,
                                 long teamId, String teamName, long seasonId,
                                 SuspensionSourceType sourceType, Long sourceFixtureId,
                                 String reason, int matchesTotal, int matchesServed, int matchesRemaining,
                                 SuspensionStatus status, LocalDateTime createdAt) {
    }
}
