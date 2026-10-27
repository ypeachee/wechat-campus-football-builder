package com.campusfootball.team;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TeamService {
    private final TeamMapper teamMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public TeamService(TeamMapper teamMapper, AuthorizationService authorizationService, AuditService auditService) {
        this.teamMapper = teamMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    public List<Team> findActiveTeams() {
        return teamMapper.findActive();
    }

    public List<Team> findAllTeams() {
        return teamMapper.findAll();
    }

    public Team getActiveTeam(long teamId) {
        return teamMapper.findById(teamId)
                .filter(team -> team.getStatus() == TeamStatus.ACTIVE)
                .orElseThrow(() -> notFound());
    }

    public Team getTeam(long teamId) {
        return teamMapper.findById(teamId).orElseThrow(this::notFound);
    }

    @Transactional
    public long createTeam(long actorUserId, String name, String shortName, String logoUrl,
                           String coachName, String homeVenue) {
        authorizationService.requireAdmin(actorUserId);
        Team team = new Team();
        team.setName(name);
        team.setShortName(shortName);
        team.setLogoUrl(logoUrl);
        team.setCoachName(coachName);
        team.setHomeVenue(homeVenue);
        team.setStatus(TeamStatus.ACTIVE);
        teamMapper.insert(team);
        auditService.record(actorUserId, team.getId(), AuditAction.CREATE, "TEAM", team.getId(), null, team);
        return team.getId();
    }

    @Transactional
    public void updateTeam(long actorUserId, long teamId, String name, String shortName, String logoUrl,
                           String coachName, String homeVenue, TeamStatus requestedStatus) {
        Team existing = teamMapper.findById(teamId).orElseThrow(this::notFound);
        TeamStatus nextStatus = existing.getStatus();
        if (authorizationService.isAdmin(actorUserId)) {
            if (requestedStatus != null) {
                nextStatus = requestedStatus;
            }
        } else {
            authorizationService.requireCanManageTeam(actorUserId, teamId);
            if (requestedStatus != null && requestedStatus != existing.getStatus()) {
                throw new AppException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "球队管理员不能修改球队状态");
            }
        }

        Team updated = copy(existing);
        updated.setName(name);
        updated.setShortName(shortName);
        updated.setLogoUrl(logoUrl);
        updated.setCoachName(coachName);
        updated.setHomeVenue(homeVenue);
        updated.setStatus(nextStatus);
        teamMapper.update(updated);
        auditService.record(actorUserId, teamId, AuditAction.UPDATE, "TEAM", teamId, existing, updated);
    }

    private Team copy(Team source) {
        Team copy = new Team();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setShortName(source.getShortName());
        copy.setLogoUrl(source.getLogoUrl());
        copy.setCoachName(source.getCoachName());
        copy.setHomeVenue(source.getHomeVenue());
        copy.setStatus(source.getStatus());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private AppException notFound() {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "球队不存在");
    }
}
