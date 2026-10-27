package com.campusfootball.rating;

import com.campusfootball.user.AppUser;
import com.campusfootball.user.AppUserMapper;
import com.campusfootball.user.UserRole;
import com.campusfootball.user.UserStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentModerationServiceTest {
    private final SensitiveTermService local = mock(SensitiveTermService.class);
    private final WechatContentSecurityClient wechat = mock(WechatContentSecurityClient.class);
    private final AppUserMapper users = mock(AppUserMapper.class);
    private final ContentModerationService service = new ContentModerationService(local, wechat, users);

    @Test
    void localBlockShortCircuitsWechat() {
        when(local.classify("禁发词")).thenReturn(ModerationResult.block("禁发词"));

        assertThat(service.evaluate(1, "禁发词").decision()).isEqualTo(ModerationDecision.BLOCK);
        verify(wechat, never()).check(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void passGoesToWechatWithUserOpenid() {
        AppUser user = new AppUser();
        user.setId(7L);
        user.setWechatOpenid("openid-7");
        user.setRoleCode(UserRole.VIEWER);
        user.setStatus(UserStatus.ACTIVE);
        when(local.classify("正常评价")).thenReturn(ModerationResult.pass());
        when(users.findById(7)).thenReturn(Optional.of(user));
        when(wechat.check("openid-7", "正常评价")).thenReturn(ModerationDecision.REVIEW);

        assertThat(service.evaluate(7, "正常评价").decision()).isEqualTo(ModerationDecision.REVIEW);
    }
}
