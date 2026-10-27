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

    public java.util.List<Long> findUserIds(long teamId) {
        return teamManagerMapper.findUserIdsByTeamId(teamId);
    }

    @Transactional
    public void grant(long actorUserId, long teamId, long targetUserId) {
        authorizationService.requireAdmin(actorUserId);
        teamMapper.findById(teamId).orElseThrow(() -> notFound("球队不存在"));
        AppUser target = appUserMapper.findById(targetUserId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> notFound("用户不存在或已停用"));
        if (teamManagerMapper.countByUserAndTeam(targetUserId, teamId) > 0) {
            throw new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "该用户已拥有球队授权");
        }
        teamManagerMapper.insertGrant(targetUserId, teamId, actorUserId);
        if (target.getRoleCode() == UserRole.VIEWER) {
            appUserMapper.updateRole(targetUserId, UserRole.TEAM_MANAGER);
        }
        auditService.record(actorUserId, teamId, AuditAction.GRANT, "TEAM_MANAGER", targetUserId, null,
                new GrantSnapshot(targetUserId, teamId));
    }

    @Transactional
    public void revoke(long actorUserId, long teamId, long targetUserId) {
        authorizationService.requireAdmin(actorUserId);
        if (teamManagerMapper.countByUserAndTeam(targetUserId, teamId) == 0) {
            throw notFound("球队授权不存在");
        }
        teamManagerMapper.deleteGrant(targetUserId, teamId);
        AppUser target = appUserMapper.findById(targetUserId).orElseThrow(() -> notFound("用户不存在"));
        if (target.getRoleCode() != UserRole.ADMIN && teamManagerMapper.countByUserId(targetUserId) == 0) {
            appUserMapper.updateRole(targetUserId, UserRole.VIEWER);
        }
        auditService.record(actorUserId, teamId, AuditAction.REVOKE, "TEAM_MANAGER", targetUserId,
                new GrantSnapshot(targetUserId, teamId), null);
    }

    private AppException notFound(String message) {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    private record GrantSnapshot(long userId, long teamId) {
    }
}
