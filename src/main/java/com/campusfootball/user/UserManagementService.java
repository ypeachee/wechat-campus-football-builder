package com.campusfootball.user;

import com.campusfootball.team.AuthorizationService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserManagementService {
    private final AppUserMapper appUserMapper;
    private final AuthorizationService authorizationService;

    public UserManagementService(AppUserMapper appUserMapper, AuthorizationService authorizationService) {
        this.appUserMapper = appUserMapper;
        this.authorizationService = authorizationService;
    }

    public List<UserSearchResponse> search(long actorUserId, String query) {
        authorizationService.requireManagementAccess(actorUserId);
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64);
        }
        return appUserMapper.searchForManagement(normalized, 20).stream()
                .map(user -> new UserSearchResponse(user.getId(), user.getNickname(),
                        user.getRoleCode(), user.getStatus()))
                .toList();
    }

    public record UserSearchResponse(long id, String nickname, UserRole role, UserStatus status) {
    }
}