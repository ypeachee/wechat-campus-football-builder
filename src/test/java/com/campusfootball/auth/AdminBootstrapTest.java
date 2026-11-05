package com.campusfootball.auth;

import com.campusfootball.user.AppUser;
import com.campusfootball.user.AppUserMapper;
import com.campusfootball.user.UserRole;
import com.campusfootball.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminBootstrapTest {
    private final AppUserMapper appUserMapper = mock(AppUserMapper.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AdminBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap = new AdminBootstrap(appUserMapper, passwordEncoder,
                "", "web-admin", "test-password", false);
    }

    @Test
    void createsNewWebAdminWhenNotFound() {
        when(appUserMapper.findByWebUsername("web-admin")).thenReturn(Optional.empty());
        when(appUserMapper.findByOpenId(anyString())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        }).when(appUserMapper).insert(any(AppUser.class));

        bootstrap.ensureWebAdmin();

        verify(appUserMapper).insert(argThat(user ->
                user.getRoleCode() == UserRole.ADMIN &&
                user.getStatus() == UserStatus.ACTIVE &&
                user.getNickname().equals("网站管理员")));
        verify(appUserMapper).updateWebCredentials(eq(1L), eq("web-admin"), anyString());
    }

    @Test
    void doesNotResetPasswordByDefault() {
        AppUser existing = new AppUser();
        existing.setId(1L);
        existing.setWebUsername("web-admin");
        existing.setRoleCode(UserRole.ADMIN);
        existing.setStatus(UserStatus.ACTIVE);
        existing.setWebPasswordHash(passwordEncoder.encode("old-password"));
        when(appUserMapper.findByWebUsername("web-admin")).thenReturn(Optional.of(existing));

        bootstrap.ensureWebAdmin();

        verify(appUserMapper, never()).updateWebCredentials(anyLong(), anyString(), anyString());
    }

    @Test
    void resetsPasswordWhenFlagIsTrue() {
        AdminBootstrap resetBootstrap = new AdminBootstrap(appUserMapper, passwordEncoder,
                "", "web-admin", "new-password", true);
        AppUser existing = new AppUser();
        existing.setId(1L);
        existing.setWebUsername("web-admin");
        existing.setRoleCode(UserRole.ADMIN);
        existing.setStatus(UserStatus.ACTIVE);
        when(appUserMapper.findByWebUsername("web-admin")).thenReturn(Optional.of(existing));

        resetBootstrap.ensureWebAdmin();

        verify(appUserMapper).updateWebCredentials(eq(1L), eq("web-admin"), argThat(hash ->
                passwordEncoder.matches("new-password", hash)));
    }

    @Test
    void doesNotPromoteNonAdminUser() {
        AppUser teamLeader = new AppUser();
        teamLeader.setId(2L);
        teamLeader.setWebUsername("web-admin");
        teamLeader.setRoleCode(UserRole.TEAM_LEADER);
        teamLeader.setStatus(UserStatus.ACTIVE);
        when(appUserMapper.findByWebUsername("web-admin")).thenReturn(Optional.of(teamLeader));

        bootstrap.ensureWebAdmin();

        verify(appUserMapper, never()).updateRole(anyLong(), any());
        verify(appUserMapper, never()).updateWebCredentials(anyLong(), anyString(), anyString());
    }

    @Test
    void rejectsUsernameLongerThan64Characters() {
        String longUsername = "a".repeat(65);
        AdminBootstrap longBootstrap = new AdminBootstrap(appUserMapper, passwordEncoder,
                "", longUsername, "test-password", false);

        longBootstrap.ensureWebAdmin();

        verifyNoInteractions(appUserMapper);
    }

    @Test
    void rejectsUsernameShorterThan3Characters() {
        AdminBootstrap shortBootstrap = new AdminBootstrap(appUserMapper, passwordEncoder,
                "", "ab", "test-password", false);

        shortBootstrap.ensureWebAdmin();

        verifyNoInteractions(appUserMapper);
    }

    @Test
    void rejectsPasswordLongerThan72Utf8Bytes() {
        String longPassword = "a".repeat(73);
        AdminBootstrap longBootstrap = new AdminBootstrap(appUserMapper, passwordEncoder,
                "", "web-admin", longPassword, false);

        longBootstrap.ensureWebAdmin();

        verifyNoInteractions(appUserMapper);
    }

    @Test
    void rejectsPasswordShorterThan8Utf8Bytes() {
        AdminBootstrap shortBootstrap = new AdminBootstrap(appUserMapper, passwordEncoder,
                "", "web-admin", "short", false);

        shortBootstrap.ensureWebAdmin();

        verifyNoInteractions(appUserMapper);
    }

    @Test
    void handlesMultiByteChinesePasswordAtBoundary() {
        String password72Bytes = "中".repeat(24);
        assertThat(password72Bytes.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isEqualTo(72);
        AdminBootstrap bootstrap72 = new AdminBootstrap(appUserMapper, passwordEncoder,
                "", "web-admin", password72Bytes, false);
        when(appUserMapper.findByWebUsername("web-admin")).thenReturn(Optional.empty());
        when(appUserMapper.findByOpenId(anyString())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        }).when(appUserMapper).insert(any(AppUser.class));

        bootstrap72.ensureWebAdmin();

        verify(appUserMapper).updateWebCredentials(eq(1L), eq("web-admin"), anyString());

        String password73Bytes = "中".repeat(24) + "a";
        AdminBootstrap bootstrap73 = new AdminBootstrap(appUserMapper, passwordEncoder,
                "", "web-admin", password73Bytes, false);

        bootstrap73.ensureWebAdmin();

        verify(appUserMapper, times(1)).updateWebCredentials(anyLong(), anyString(), anyString());
    }

    @Test
    void usesHashedOpenidWhenUsernameFitsButPrefixExceeds64Bytes() {
        String username60Chars = "a".repeat(60);
        AdminBootstrap bootstrap60 = new AdminBootstrap(appUserMapper, passwordEncoder,
                "", username60Chars, "test-password", false);
        when(appUserMapper.findByWebUsername(username60Chars)).thenReturn(Optional.empty());
        when(appUserMapper.findByOpenId(anyString())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        }).when(appUserMapper).insert(any(AppUser.class));

        bootstrap60.ensureWebAdmin();

        verify(appUserMapper).insert(argThat(user ->
                user.getWechatOpenid().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 64 &&
                user.getWechatOpenid().startsWith("web-admin:")));
        verify(appUserMapper).updateWebCredentials(eq(1L), eq(username60Chars), anyString());
    }

    @Test
    void skipsWhenUsernameOrPasswordBlank() {
        AdminBootstrap emptyBootstrap = new AdminBootstrap(appUserMapper, passwordEncoder,
                "", "", "", false);

        emptyBootstrap.ensureWebAdmin();

        verifyNoInteractions(appUserMapper);
    }

    @Test
    void findByOpenIdFallbackWhenUsernameNotFound() {
        String syntheticOpenid = "web-admin:web-admin";
        when(appUserMapper.findByWebUsername("web-admin")).thenReturn(Optional.empty());
        AppUser existingByOpenid = new AppUser();
        existingByOpenid.setId(3L);
        existingByOpenid.setWechatOpenid(syntheticOpenid);
        existingByOpenid.setRoleCode(UserRole.ADMIN);
        existingByOpenid.setStatus(UserStatus.ACTIVE);
        when(appUserMapper.findByOpenId(syntheticOpenid)).thenReturn(Optional.of(existingByOpenid));

        bootstrap.ensureWebAdmin();

        verify(appUserMapper, never()).insert(any());
        verify(appUserMapper, never()).updateWebCredentials(anyLong(), anyString(), anyString());
    }
}
