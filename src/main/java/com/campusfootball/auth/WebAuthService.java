package com.campusfootball.auth;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.common.security.JwtService;
import com.campusfootball.team.TeamManagerMapper;
import com.campusfootball.user.AppUser;
import com.campusfootball.user.AppUserMapper;
import com.campusfootball.user.UserRole;
import com.campusfootball.user.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class WebAuthService {
    private static final String INVALID_CREDENTIALS = "账号或密码不正确";

    private final AppUserMapper appUserMapper;
    private final TeamManagerMapper teamManagerMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public WebAuthService(AppUserMapper appUserMapper,
                          TeamManagerMapper teamManagerMapper,
                          JwtService jwtService,
                          PasswordEncoder passwordEncoder) {
        this.appUserMapper = appUserMapper;
        this.teamManagerMapper = teamManagerMapper;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse login(String username, String password) {
        String normalizedUsername = username == null ? "" : username.trim();
        AppUser user = appUserMapper.findByWebUsername(normalizedUsername)
                .orElseThrow(this::invalidCredentials);
        if (user.getStatus() != UserStatus.ACTIVE
                || user.getWebPasswordHash() == null
                || !passwordEncoder.matches(password, user.getWebPasswordHash())) {
            throw invalidCredentials();
        }
        if (user.getRoleCode() == UserRole.VIEWER) {
            throw new AppException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "该账号没有网站管理权限");
        }
        return new AuthResponse(
                jwtService.createToken(user.getId()),
                user.getId(),
                user.getNickname(),
                user.getRoleCode(),
                teamManagerMapper.findTeamIdsByUserId(user.getId()));
    }

    private AppException invalidCredentials() {
        return new AppException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, INVALID_CREDENTIALS);
    }
}
