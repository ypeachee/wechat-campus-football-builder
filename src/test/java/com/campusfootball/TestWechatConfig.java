package com.campusfootball;

import com.campusfootball.auth.WechatAuthClient;
import com.campusfootball.auth.WechatIdentity;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.rating.ModerationDecision;
import com.campusfootball.rating.WechatContentSecurityClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;

import java.util.Map;

@TestConfiguration
public class TestWechatConfig {
    private static final Map<String, WechatIdentity> IDENTITIES = Map.of(
            "manager-code", new WechatIdentity("openid-manager", null),
            "viewer-code", new WechatIdentity("openid-viewer", null),
            "admin-code", new WechatIdentity("openid-admin", null),
            "authorized-manager", new WechatIdentity("openid-authorized-manager", null),
            "manager-target", new WechatIdentity("openid-manager-target", null),
            "leader-code", new WechatIdentity("openid-leader", null),
            "leader-target", new WechatIdentity("openid-leader-target", null)
    );

    @Bean
    @Primary
    WechatContentSecurityClient testWechatContentSecurityClient() {
        return (openid, content) -> ModerationDecision.PASS;
    }

    @Bean
    @Primary
    WechatAuthClient testWechatAuthClient() {
        return code -> {
            WechatIdentity identity = IDENTITIES.get(code);
            if (identity == null) {
                throw new AppException(HttpStatus.UNAUTHORIZED, ErrorCode.WECHAT_LOGIN_FAILED, "微信登录失败");
            }
            return identity;
        };
    }
}
