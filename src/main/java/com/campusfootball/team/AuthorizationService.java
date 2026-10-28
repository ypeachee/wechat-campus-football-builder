package com.campusfootball.team;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.user.AppUser;
import com.campusfootball.user.AppUserMapper;
import com.campusfootball.user.UserRole;
import com.campusfootball.user.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthorizationService {
    private final AppUserMapper appUserMapper;
    private final TeamManagerMapper teamManagerMapper;

    public AuthorizationService(AppUserMapper appUserMapper, TeamManagerMapper teamManagerMapper) {
        this.appUserMapper = appUserMapper;
        this.teamManagerMapper = teamManagerMapper;
    }

    public void requireAdmin(long userId) {
        if (currentRole(userId) != UserRole.ADMIN) {
            throw forbidden();
        }
    }

    public void requireManagementAccess(long userId) {
        UserRole role = currentRole(userId);
        if (role != UserRole.ADMIN && role != UserRole.TEAM_MANAGER) {
            throw forbidden();
        }
    }

    public void requireCanManageTeam(long userId, long teamId) {
        UserRole role = currentRole(userId);
        if (role == UserRole.ADMIN || role == UserRole.TEAM_MANAGER) {
            return;
        }
        if (role != UserRole.TEAM_LEADER
                || teamManagerMapper.countByUserAndTeam(userId, teamId) == 0) {
            throw forbidden();
        }
    }

    public boolean isAdmin(long userId) {
        return currentRole(userId) == UserRole.ADMIN;
    }

    public boolean isManagementAdmin(long userId) {
        UserRole role = currentRole(userId);
        return role == UserRole.ADMIN || role == UserRole.TEAM_MANAGER;
    }

    public boolean isTeamLeader(long userId) {
        return currentRole(userId) == UserRole.TEAM_LEADER;
    }

    public UserRole currentRole(long userId) {
        return requireActiveUser(userId).getRoleCode();
    }

    public List<Long> managedTeamIds(long userId) {
        return teamManagerMapper.findTeamIdsByUserId(userId);
    }

    private AppUser requireActiveUser(long userId) {
        return appUserMapper.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "用户不存在或已停用"));
    }

    private AppException forbidden() {
        return new AppException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "没有权限执行该操作");
    }
}