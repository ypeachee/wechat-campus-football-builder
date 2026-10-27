package com.campusfootball.auth;

import com.campusfootball.user.AppUser;
import com.campusfootball.user.AppUserMapper;
import com.campusfootball.user.UserRole;
import com.campusfootball.user.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final AppUserMapper appUserMapper;
    private final String adminOpenid;

    public AdminBootstrap(AppUserMapper appUserMapper,
                          @Value("${app.bootstrap.admin-openid:}") String adminOpenid) {
        this.appUserMapper = appUserMapper;
        this.adminOpenid = adminOpenid;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureAdmin();
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
}
