package com.campusfootball.common.security;

import com.campusfootball.user.UserRole;

public record UserPrincipal(long userId, UserRole role) {
}
