package com.campusfootball.user;

import com.campusfootball.common.api.AppException;
import com.campusfootball.team.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebCredentialServiceTest {
    private final AppUserMapper appUserMapper = mock(AppUserMapper.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private WebCredentialService service;

    @BeforeEach
    void setUp() {
        service = new WebCredentialService(appUserMapper, authorizationService, passwordEncoder);
    }

    @Test
    void adminCanSetCredentialsForTeamLeader() {
        AppUser target = user(10L, UserRole.TEAM_LEADER);
        when(appUserMapper.findById(10L)).thenReturn(Optional.of(target));
        when(appUserMapper.findByWebUsername("leader-web")).thenReturn(Optional.empty());

        WebCredentialService.WebCredentialResponse response =
                service.setCredentials(1L, 10L, "leader-web", "strong-pass-2026");

        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.username()).isEqualTo("leader-web");
        assertThat(response.role()).isEqualTo(UserRole.TEAM_LEADER);
        verify(appUserMapper).updateWebCredentials(eq(10L), eq("leader-web"), anyString());
    }

    @Test
    void nonAdminIsRejected() {
        doThrow(new AppException(org.springframework.http.HttpStatus.FORBIDDEN,
                com.campusfootball.common.api.ErrorCode.FORBIDDEN, "forbidden"))
                .when(authorizationService).requireAdmin(2L);

        assertThatThrownBy(() -> service.setCredentials(2L, 10L, "user", "strong-pass-2026"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus().value()).isEqualTo(403));
    }

    @Test
    void cannotSetCredentialsForViewer() {
        AppUser viewer = user(10L, UserRole.VIEWER);
        when(appUserMapper.findById(10L)).thenReturn(Optional.of(viewer));

        assertThatThrownBy(() -> service.setCredentials(1L, 10L, "viewer-web", "strong-pass-2026"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus().value()).isEqualTo(400));
    }

    @Test
    void usernameTooShortIsRejected() {
        AppUser target = user(10L, UserRole.TEAM_LEADER);
        when(appUserMapper.findById(10L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.setCredentials(1L, 10L, "ab", "strong-pass-2026"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus().value()).isEqualTo(400));
    }

    @Test
    void passwordTooShortIsRejected() {
        AppUser target = user(10L, UserRole.TEAM_LEADER);
        when(appUserMapper.findById(10L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.setCredentials(1L, 10L, "leader-web", "short"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus().value()).isEqualTo(400));
    }

    @Test
    void duplicateUsernameReturnsConflict() {
        AppUser target = user(10L, UserRole.TEAM_LEADER);
        AppUser existing = user(20L, UserRole.ADMIN);
        existing.setWebUsername("taken-name");
        when(appUserMapper.findById(10L)).thenReturn(Optional.of(target));
        when(appUserMapper.findByWebUsername("taken-name")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.setCredentials(1L, 10L, "taken-name", "strong-pass-2026"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus().value()).isEqualTo(409));
    }

    @Test
    void sameUserCanUpdateOwnUsername() {
        AppUser target = user(10L, UserRole.TEAM_LEADER);
        target.setWebUsername("old-name");
        AppUser sameUserWithNewName = user(10L, UserRole.TEAM_LEADER);
        sameUserWithNewName.setWebUsername("new-name");
        when(appUserMapper.findById(10L)).thenReturn(Optional.of(target));
        when(appUserMapper.findByWebUsername("new-name")).thenReturn(Optional.of(sameUserWithNewName));

        WebCredentialService.WebCredentialResponse response =
                service.setCredentials(1L, 10L, "new-name", "strong-pass-2026");

        assertThat(response.username()).isEqualTo("new-name");
        verify(appUserMapper).updateWebCredentials(eq(10L), eq("new-name"), anyString());
    }

    @Test
    void usernameTrimmedBeforeValidation() {
        AppUser target = user(10L, UserRole.TEAM_LEADER);
        when(appUserMapper.findById(10L)).thenReturn(Optional.of(target));
        when(appUserMapper.findByWebUsername("leader")).thenReturn(Optional.empty());

        service.setCredentials(1L, 10L, "  leader  ", "strong-pass-2026");

        verify(appUserMapper).updateWebCredentials(eq(10L), eq("leader"), anyString());
    }

    @Test
    void emptyUsernameAfterTrimIsRejected() {
        AppUser target = user(10L, UserRole.TEAM_LEADER);
        when(appUserMapper.findById(10L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.setCredentials(1L, 10L, "   ", "strong-pass-2026"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus().value()).isEqualTo(400));
    }

    @Test
    void usernameAt64CharactersIsAccepted() {
        String username64 = "a".repeat(64);
        AppUser target = user(10L, UserRole.TEAM_LEADER);
        when(appUserMapper.findById(10L)).thenReturn(Optional.of(target));
        when(appUserMapper.findByWebUsername(username64)).thenReturn(Optional.empty());

        service.setCredentials(1L, 10L, username64, "strong-pass-2026");

        verify(appUserMapper).updateWebCredentials(eq(10L), eq(username64), anyString());
    }

    @Test
    void usernameAt65CharactersIsRejected() {
        String username65 = "a".repeat(65);
        AppUser target = user(10L, UserRole.TEAM_LEADER);
        when(appUserMapper.findById(10L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.setCredentials(1L, 10L, username65, "strong-pass-2026"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus().value()).isEqualTo(400));
    }

    @Test
    void passwordAt72Utf8BytesIsAccepted() {
        String password72Bytes = "中".repeat(24);
        assertThat(password72Bytes.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isEqualTo(72);
        AppUser target = user(10L, UserRole.TEAM_LEADER);
        when(appUserMapper.findById(10L)).thenReturn(Optional.of(target));
        when(appUserMapper.findByWebUsername("leader-web")).thenReturn(Optional.empty());

        service.setCredentials(1L, 10L, "leader-web", password72Bytes);

        verify(appUserMapper).updateWebCredentials(eq(10L), eq("leader-web"), anyString());
    }

    @Test
    void passwordAt73Utf8BytesIsRejected() {
        String password73Bytes = "中".repeat(24) + "a";
        assertThat(password73Bytes.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isEqualTo(73);
        AppUser target = user(10L, UserRole.TEAM_LEADER);
        when(appUserMapper.findById(10L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.setCredentials(1L, 10L, "leader-web", password73Bytes))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus().value()).isEqualTo(400));
    }

    private AppUser user(long id, UserRole role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setRoleCode(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setNickname("测试用户");
        return user;
    }
}
