package com.campusfootball.team;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class PlayerService {
    private final PlayerMapper playerMapper;
    private final TeamMapper teamMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public PlayerService(PlayerMapper playerMapper, TeamMapper teamMapper,
                         AuthorizationService authorizationService, AuditService auditService) {
        this.playerMapper = playerMapper;
        this.teamMapper = teamMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    public List<Player> findActiveByTeam(long teamId) {
        return playerMapper.findActiveByTeamId(teamId);
    }

    public List<Player> findAllByTeam(long teamId) {
        return playerMapper.findByTeamId(teamId);
    }

    public Player getActivePlayer(long playerId) {
        Player player = playerMapper.findById(playerId)
                .filter(candidate -> candidate.getStatus() == PlayerStatus.ACTIVE)
                .orElseThrow(this::notFound);
        Team team = teamMapper.findById(player.getTeamId()).orElseThrow(this::notFound);
        if (team.getStatus() != TeamStatus.ACTIVE) {
            throw notFound();
        }
        return player;
    }

    public Player getPlayer(long playerId) {
        return playerMapper.findById(playerId).orElseThrow(this::notFound);
    }

    @Transactional
    public long createPlayer(long actorUserId, long teamId, String name, Integer jerseyNo,
                             String position, LocalDate birthDate, String photoUrl) {
        authorizationService.requireCanManageTeam(actorUserId, teamId);
        teamMapper.findById(teamId).orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "球队不存在"));
        ensureJerseyAvailable(teamId, jerseyNo, null);
        Player player = new Player();
        player.setTeamId(teamId);
        player.setName(name);
        player.setJerseyNo(jerseyNo);
        player.setPosition(position);
        player.setBirthDate(birthDate);
        player.setPhotoUrl(photoUrl);
        player.setStatus(PlayerStatus.ACTIVE);
        playerMapper.insert(player);
        auditService.record(actorUserId, teamId, AuditAction.CREATE, "PLAYER", player.getId(), null, player);
        return player.getId();
    }

    @Transactional
    public void updatePlayer(long actorUserId, long playerId, String name, Integer jerseyNo,
                             String position, LocalDate birthDate, String photoUrl, PlayerStatus requestedStatus) {
        Player existing = playerMapper.findById(playerId).orElseThrow(this::notFound);
        authorizationService.requireCanManageTeam(actorUserId, existing.getTeamId());
        ensureJerseyAvailable(existing.getTeamId(), jerseyNo, playerId);

        Player updated = copy(existing);
        updated.setName(name);
        updated.setJerseyNo(jerseyNo);
        updated.setPosition(position);
        updated.setBirthDate(birthDate);
        updated.setPhotoUrl(photoUrl);
        updated.setStatus(requestedStatus == null ? existing.getStatus() : requestedStatus);
        playerMapper.update(updated);
        auditService.record(actorUserId, existing.getTeamId(), AuditAction.UPDATE, "PLAYER", playerId, existing, updated);
    }

    private void ensureJerseyAvailable(long teamId, Integer jerseyNo, Long excludeId) {
        if (jerseyNo == null) {
            return;
        }
        if (playerMapper.countActiveJersey(teamId, jerseyNo, excludeId) > 0) {
            throw new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "球队内球衣号码已被占用");
        }
    }

    private Player copy(Player source) {
        Player copy = new Player();
        copy.setId(source.getId());
        copy.setTeamId(source.getTeamId());
        copy.setName(source.getName());
        copy.setJerseyNo(source.getJerseyNo());
        copy.setPosition(source.getPosition());
        copy.setBirthDate(source.getBirthDate());
        copy.setPhotoUrl(source.getPhotoUrl());
        copy.setStatus(source.getStatus());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private AppException notFound() {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "球员不存在");
    }
}
