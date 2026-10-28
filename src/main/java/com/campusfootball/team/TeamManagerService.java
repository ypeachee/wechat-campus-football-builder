package com.campusfootball.team;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.user.AppUser;
import com.campusfootball.user.AppUserMapper;
import com.campusfootball.user.UserRole;
import com.campusfootball.user.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TeamManagerService {
    private final TeamManagerMapper teamManagerMapper;
    private final TeamMapper teamMapper;
    private final AppUserMapper appUserMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public TeamManagerService(TeamManagerMapper teamManagerMapper, TeamMapper teamMapper,
                              AppUserMapper appUserMapper, AuthorizationService authorizationService,
                              AuditService auditService) {
        this.teamManagerMapper = teamManagerMapper;
        this.teamMapper = teamMapper;
        this.appUserMapper = appUserMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    public List<TeamManagerAssignment> findAssignments(long teamId) {
        return teamManagerMapper.findAssignmentsByTeamId(teamId);
    }

    @Transactional
    public void grant(long actorUserId, long teamId, long targetUserId, UserRole assignmentRole) {
        authorizationService.requireManagementAccess(actorUserId);
        if (assignmentRole != UserRole.TEAM_MANAGER && assignmentRole != UserRole.TEAM_LEADER) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "球队授权角色必须是授权管理员或领队");
        }
        teamMapper.findById(teamId).orElseThrow(() -> notFound("球队不存在"));
        AppUser target = appUserMapper.findById(targetUserId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> notFound("用户不存在或已停用"));
        if (teamManagerMapper.countByUserAndTeam(targetUserId, teamId) > 0) {
            throw new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "该用户已拥有球队授权");
        }
        teamManagerMapper.insertGrant(targetUserId, teamId, actorUserId, assignmentRole);
        if (target.getRoleCode() != UserRole.ADMIN) {
            updateGlobalRole(targetUserId);
        }
        auditService.record(actorUserId, teamId, AuditAction.GRANT, "TEAM_MANAGER", targetUserId, null,
                new GrantSnapshot(targetUserId, teamId, assignmentRole));
    }

    @Transactional
    public void revoke(long actorUserId, long teamId, long targetUserId) {
        authorizationService.requireManagementAccess(actorUserId);
        UserRole assignmentRole = teamManagerMapper.findRoleByUserAndTeam(targetUserId, teamId);
        if (assignmentRole == null) {
            throw notFound("球队授权不存在");
        }
        teamManagerMapper.deleteGrant(targetUserId, teamId);
        AppUser target = appUserMapper.findById(targetUserId).orElseThrow(() -> notFound("用户不存在"));
        if (target.getRoleCode() != UserRole.ADMIN) {
            updateGlobalRole(targetUserId);
        }
        auditService.record(actorUserId, teamId, AuditAction.REVOKE, "TEAM_MANAGER", targetUserId,
                new GrantSnapshot(targetUserId, teamId, assignmentRole), null);
    }

    private void updateGlobalRole(long userId) {
        List<UserRole> roles = teamManagerMapper.findRoleCodesByUserId(userId);
        UserRole effectiveRole = roles.contains(UserRole.TEAM_MANAGER)
                ? UserRole.TEAM_MANAGER
                : roles.contains(UserRole.TEAM_LEADER) ? UserRole.TEAM_LEADER : UserRole.VIEWER;
        appUserMapper.updateRole(userId, effectiveRole);
    }

    private AppException notFound(String message) {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    private record GrantSnapshot(long userId, long teamId, UserRole role) {
    }
}