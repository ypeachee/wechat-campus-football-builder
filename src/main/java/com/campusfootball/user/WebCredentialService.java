package com.campusfootball.user;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.team.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class WebCredentialService {
    private final AppUserMapper appUserMapper;
    private final AuthorizationService authorizationService;
    private final PasswordEncoder passwordEncoder;

    public WebCredentialService(AppUserMapper appUserMapper,
                                AuthorizationService authorizationService,
                                PasswordEncoder passwordEncoder) {
        this.appUserMapper = appUserMapper;
        this.authorizationService = authorizationService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public WebCredentialResponse setCredentials(long actorUserId, long targetUserId,
                                                String username, String password) {
        authorizationService.requireAdmin(actorUserId);
        AppUser user = appUserMapper.findById(targetUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "用户不存在"));
        if (user.getRoleCode() == UserRole.VIEWER) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "只能为管理员、球队管理员或领队配置网站账号");
        }
        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedUsername.length() < 3 || normalizedUsername.length() > 64) {
            throw validation("网站账号长度应为 3-64 个字符");
        }
        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw validation("密码至少 8 个字符，且 UTF-8 编码不能超过 72 字节");
        }
        appUserMapper.findByWebUsername(normalizedUsername)
                .filter(existing -> existing.getId() != targetUserId)
                .ifPresent(existing -> {
                    throw new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "网站账号已被使用");
                });
        appUserMapper.updateWebCredentials(targetUserId, normalizedUsername, passwordEncoder.encode(password));
        return new WebCredentialResponse(targetUserId, normalizedUsername, user.getRoleCode());
    }

    private AppException validation(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }

    public record WebCredentialResponse(long userId, String username, UserRole role) {
    }
}
