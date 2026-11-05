package com.campusfootball.tactic;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.team.AuthorizationService;
import com.campusfootball.team.Player;
import com.campusfootball.team.PlayerMapper;
import com.campusfootball.team.PlayerStatus;
import com.campusfootball.team.TeamMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class TeamTacticService {
    private static final int MAX_PLAYERS = 30;
    private static final TypeReference<List<TacticPosition>> POSITION_LIST = new TypeReference<>() { };

    private final TeamTacticMapper tacticMapper;
    private final TeamMapper teamMapper;
    private final PlayerMapper playerMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public TeamTacticService(TeamTacticMapper tacticMapper,
                             TeamMapper teamMapper,
                             PlayerMapper playerMapper,
                             AuthorizationService authorizationService,
                             AuditService auditService,
                             ObjectMapper objectMapper) {
        this.tacticMapper = tacticMapper;
        this.teamMapper = teamMapper;
        this.playerMapper = playerMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<TacticResponse> findByTeam(long actorUserId, long teamId) {
        authorizationService.requireCanManageTeam(actorUserId, teamId);
        requireTeam(teamId);
        return tacticMapper.findByTeamId(teamId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public TacticResponse create(long actorUserId, long teamId, String name, List<TacticPosition> positions) {
        authorizationService.requireCanManageTeam(actorUserId, teamId);
        requireTeam(teamId);
        String normalizedName = validateName(name);
        List<TacticPosition> normalizedPositions = validatePositions(teamId, positions);
        tacticMapper.findByTeamIdAndName(teamId, normalizedName).ifPresent(existing -> {
            throw new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "同名战术板已存在");
        });

        TeamTactic tactic = new TeamTactic();
        tactic.setTeamId(teamId);
        tactic.setName(normalizedName);
        tactic.setPositionsJson(toJson(normalizedPositions));
        tactic.setCreatedBy(actorUserId);
        tactic.setUpdatedBy(actorUserId);
        tacticMapper.insert(tactic);
        TacticResponse response = toResponse(tactic);
        auditService.record(actorUserId, teamId, AuditAction.CREATE, "TEAM_TACTIC", tactic.getId(), null, response);
        return response;
    }

    @Transactional
    public TacticResponse update(long actorUserId, long tacticId, String name, List<TacticPosition> positions) {
        TeamTactic existing = tacticMapper.findById(tacticId).orElseThrow(this::notFound);
        authorizationService.requireCanManageTeam(actorUserId, existing.getTeamId());
        String normalizedName = validateName(name);
        List<TacticPosition> normalizedPositions = validatePositions(existing.getTeamId(), positions);
        tacticMapper.findByTeamIdAndName(existing.getTeamId(), normalizedName)
                .filter(other -> !other.getId().equals(tacticId))
                .ifPresent(other -> {
                    throw new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "同名战术板已存在");
                });

        TacticResponse before = toResponse(existing);
        existing.setName(normalizedName);
        existing.setPositionsJson(toJson(normalizedPositions));
        existing.setUpdatedBy(actorUserId);
        tacticMapper.update(existing);
        TacticResponse after = toResponse(tacticMapper.findById(tacticId).orElseThrow(this::notFound));
        auditService.record(actorUserId, existing.getTeamId(), AuditAction.UPDATE, "TEAM_TACTIC", tacticId, before, after);
        return after;
    }

    @Transactional
    public void delete(long actorUserId, long tacticId) {
        TeamTactic existing = tacticMapper.findById(tacticId).orElseThrow(this::notFound);
        authorizationService.requireCanManageTeam(actorUserId, existing.getTeamId());
        tacticMapper.deleteById(tacticId);
        auditService.record(actorUserId, existing.getTeamId(), AuditAction.DELETE, "TEAM_TACTIC", tacticId, toResponse(existing), null);
    }

    private void requireTeam(long teamId) {
        teamMapper.findById(teamId).orElseThrow(() ->
                new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "球队不存在"));
    }

    private String validateName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > 80) {
            throw validation("战术板名称应为 1-80 个字符");
        }
        return normalized;
    }

    private List<TacticPosition> validatePositions(long teamId, List<TacticPosition> positions) {
        if (positions == null) {
            throw validation("球员位置不能为空");
        }
        if (positions.size() > MAX_PLAYERS) {
            throw validation("单个战术板最多放置 " + MAX_PLAYERS + " 名球员");
        }
        Set<Long> playerIds = new HashSet<>();
        for (TacticPosition position : positions) {
            if (position == null || position.playerId() == null || position.x() == null || position.y() == null) {
                throw validation("球员位置数据不完整");
            }
            if (!Double.isFinite(position.x()) || !Double.isFinite(position.y())
                    || position.x() < 0 || position.x() > 100
                    || position.y() < 0 || position.y() > 100) {
                throw validation("球员坐标必须在 0-100 之间");
            }
            if (!playerIds.add(position.playerId())) {
                throw validation("同一球员不能重复放置");
            }
        }
        Set<Long> activeTeamPlayerIds = new HashSet<>();
        for (Player player : playerMapper.findByTeamId(teamId)) {
            if (player.getStatus() == PlayerStatus.ACTIVE) {
                activeTeamPlayerIds.add(player.getId());
            }
        }
        if (!activeTeamPlayerIds.containsAll(playerIds)) {
            throw validation("战术板包含不属于该球队或已停用的球员");
        }
        return new ArrayList<>(positions);
    }

    private TacticResponse toResponse(TeamTactic tactic) {
        return new TacticResponse(tactic.getId(), tactic.getTeamId(), tactic.getName(),
                fromJson(tactic.getPositionsJson()), tactic.getUpdatedBy(), tactic.getUpdatedAt());
    }

    private String toJson(List<TacticPosition> positions) {
        try {
            return objectMapper.writeValueAsString(positions);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize tactic positions", ex);
        }
    }

    private List<TacticPosition> fromJson(String json) {
        try {
            return objectMapper.readValue(json, POSITION_LIST);
        } catch (JsonProcessingException ex) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "战术板数据损坏");
        }
    }

    private AppException validation(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }

    private AppException notFound() {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "战术板不存在");
    }

    public record TacticPosition(
            @NotNull Long playerId,
            @NotNull @DecimalMin("0") @DecimalMax("100") Double x,
            @NotNull @DecimalMin("0") @DecimalMax("100") Double y) {
    }

    public record TacticResponse(long id, long teamId, String name, List<TacticPosition> positions,
                                 long updatedBy, LocalDateTime updatedAt) {
    }
}
