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

@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final String adminOpenid;
    private final String webAdminUsername;
    private final String webAdminPassword;

    public AdminBootstrap(AppUserMapper appUserMapper,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.bootstrap.admin-openid:}") String adminOpenid,
                          @Value("${app.bootstrap.web-admin-username:}") String webAdminUsername,
                          @Value("${app.bootstrap.web-admin-password:}") String webAdminPassword) {
        this.appUserMapper = appUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.adminOpenid = adminOpenid;
        this.webAdminUsername = webAdminUsername;
        this.webAdminPassword = webAdminPassword;
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
        String syntheticOpenid = "web-admin:" + username;
        AppUser user = appUserMapper.findByWebUsername(username)
                .or(() -> appUserMapper.findByOpenId(syntheticOpenid))
                .orElseGet(() -> {
                    AppUser created = new AppUser();
                    created.setWechatOpenid(syntheticOpenid);
                    created.setNickname("网站管理员");
                    created.setRoleCode(UserRole.ADMIN);
                    created.setStatus(UserStatus.ACTIVE);
                    appUserMapper.insert(created);
                    return created;
                });
        if (user.getRoleCode() != UserRole.ADMIN) {
            appUserMapper.updateRole(user.getId(), UserRole.ADMIN);
        }
        appUserMapper.updateWebCredentials(user.getId(), username, passwordEncoder.encode(webAdminPassword));
        log.info("Web administrator account is ready: {}", username);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
