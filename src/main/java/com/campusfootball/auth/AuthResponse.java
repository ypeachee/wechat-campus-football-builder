package com.campusfootball.auth;

import com.campusfootball.user.UserRole;

import java.util.List;

public record AuthResponse(String token, long userId, String nickname, UserRole role, List<Long> managedTeamIds) {
}
