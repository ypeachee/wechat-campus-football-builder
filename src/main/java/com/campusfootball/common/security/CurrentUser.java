package com.campusfootball.common.security;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CurrentUser {
    public UserPrincipal require() {
        return optional().orElseThrow(() -> new AppException(
                HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "请先登录"));
    }

    public Optional<UserPrincipal> optional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }
}
