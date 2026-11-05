package com.campusfootball.auth;

import com.campusfootball.user.AppUser;
import com.campusfootball.user.AppUserMapper;
import com.campusfootball.user.UserRole;
import com.campusfootball.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int OPENID_MAX_LENGTH = 64;
    private static final int USERNAME_MIN_LENGTH = 3;
    private static final int USERNAME_MAX_LENGTH = 64;
    private static final int PASSWORD_MIN_BYTES = 8;
    private static final int PASSWORD_MAX_BYTES = 72;
    private static final String SYNTHETIC_OPENID_PREFIX = "web-admin:";

    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final String adminOpenid;
    private final String webAdminUsername;
    private final String webAdminPassword;
    private final boolean resetWebAdminPassword;

    public AdminBootstrap(AppUserMapper appUserMapper,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.bootstrap.admin-openid:}") String adminOpenid,
                          @Value("${app.bootstrap.web-admin-username:}") String webAdminUsername,
                          @Value("${app.bootstrap.web-admin-password:}") String webAdminPassword,
                          @Value("${app.bootstrap.web-admin-reset-password:false}") boolean resetWebAdminPassword) {
        this.appUserMapper = appUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.adminOpenid = adminOpenid;
        this.webAdminUsername = webAdminUsername;
        this.webAdminPassword = webAdminPassword;
        this.resetWebAdminPassword = resetWebAdminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureAdmin();
        ensureWebAdmin();
    }

    public void ensureAdmin() {
        if (adminOpenid == null || adminOpenid.isBlank()) {
            return;
        }
        AppUser user = appUserMapper.findByOpenId(adminOpenid).orElseGet(() -> {
            AppUser created = new AppUser();
            created.setWechatOpenid(adminOpenid);
            created.setRoleCode(UserRole.ADMIN);
            created.setStatus(UserStatus.ACTIVE);
            appUserMapper.insert(created);
            return created;
        });
        if (user.getRoleCode() != UserRole.ADMIN) {
            appUserMapper.updateRole(user.getId(), UserRole.ADMIN);
        }
    }

    public void ensureWebAdmin() {
        String username = normalize(webAdminUsername);
        if (username.isEmpty() || webAdminPassword == null || webAdminPassword.isBlank()) {
            return;
        }
        if (username.length() < USERNAME_MIN_LENGTH || username.length() > USERNAME_MAX_LENGTH) {
            log.error("Web admin username must be {}-{} characters, got {} characters. Skipping bootstrap.",
                    USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH, username.length());
            return;
        }
        int passwordBytes = webAdminPassword.getBytes(StandardCharsets.UTF_8).length;
        if (passwordBytes < PASSWORD_MIN_BYTES || passwordBytes > PASSWORD_MAX_BYTES) {
            log.error("Web admin password must be {}-{} UTF-8 bytes, got {} bytes. Skipping bootstrap.",
                    PASSWORD_MIN_BYTES, PASSWORD_MAX_BYTES, passwordBytes);
            return;
        }
        String syntheticOpenid = buildSyntheticOpenid(username);
        AppUser existingByUsername = appUserMapper.findByWebUsername(username).orElse(null);
        AppUser existingByOpenid = appUserMapper.findByOpenId(syntheticOpenid).orElse(null);
        AppUser user = existingByUsername != null ? existingByUsername : existingByOpenid;
        if (user == null) {
            AppUser created = new AppUser();
            created.setWechatOpenid(syntheticOpenid);
            created.setNickname("网站管理员");
            created.setRoleCode(UserRole.ADMIN);
            created.setStatus(UserStatus.ACTIVE);
            appUserMapper.insert(created);
            user = created;
            log.info("Created web administrator account: {}", username);
        } else if (user.getRoleCode() != UserRole.ADMIN) {
            log.warn("Refusing to promote existing non-admin user '{}' (role={}) to ADMIN. " +
                     "Set role manually or use a different username.", username, user.getRoleCode());
            return;
        }
        if (resetWebAdminPassword || existingByUsername == null && existingByOpenid == null) {
            appUserMapper.updateWebCredentials(user.getId(), username, passwordEncoder.encode(webAdminPassword));
            if (resetWebAdminPassword) {
                log.info("Reset password for web administrator: {}", username);
            }
        }
        log.info("Web administrator account is ready: {}", username);
    }

    private String buildSyntheticOpenid(String username) {
        String candidate = SYNTHETIC_OPENID_PREFIX + username;
        if (candidate.getBytes(StandardCharsets.UTF_8).length <= OPENID_MAX_LENGTH) {
            return candidate;
        }
        return SYNTHETIC_OPENID_PREFIX + sha256Truncated(username, OPENID_MAX_LENGTH - SYNTHETIC_OPENID_PREFIX.length());
    }

    private String sha256Truncated(String value, int maxBytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hash);
            while (hex.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
                hex = hex.substring(0, hex.length() - 1);
            }
            return hex;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
