package com.campusfootball.auth;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.security.JwtService;
import com.campusfootball.team.TeamManagerMapper;
import com.campusfootball.user.AppUser;
import com.campusfootball.user.AppUserMapper;
import com.campusfootball.user.UserRole;
import com.campusfootball.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebAuthServiceTest {
    private final AppUserMapper appUserMapper = mock(AppUserMapper.class);
    private final TeamManagerMapper teamManagerMapper = mock(TeamManagerMapper.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private WebAuthService service;

    @BeforeEach
    void setUp() {
        service = new WebAuthService(appUserMapper, teamManagerMapper, jwtService, passwordEncoder);
    }

    @Test
    void leaderCanLoginWithConfiguredCredentials() {
        AppUser user = user(UserRole.TEAM_LEADER, passwordEncoder.encode("strong-pass"));
        when(appUserMapper.findByWebUsername("team-leader")).thenReturn(Optional.of(user));
        when(teamManagerMapper.findTeamIdsByUserId(11L)).thenReturn(List.of(3L, 8L));
        when(jwtService.createToken(11L)).thenReturn("signed-token");

        AuthResponse response = service.login(" team-leader ", "strong-pass");

        assertThat(response.token()).isEqualTo("signed-token");
        assertThat(response.role()).isEqualTo(UserRole.TEAM_LEADER);
        assertThat(response.managedTeamIds()).containsExactly(3L, 8L);
    }

    @Test
    void wrongPasswordIsRejected() {
        AppUser user = user(UserRole.ADMIN, passwordEncoder.encode("strong-pass"));
        when(appUserMapper.findByWebUsername("admin")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login("admin", "wrong-pass"))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getStatus().value()).isEqualTo(401));
    }

    @Test
    void viewerCannotUseManagementLogin() {
        AppUser user = user(UserRole.VIEWER, passwordEncoder.encode("strong-pass"));
        when(appUserMapper.findByWebUsername("viewer")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login("viewer", "strong-pass"))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getStatus().value()).isEqualTo(403));
    }

    @Test
    void disabledUserIsRejected() {
        AppUser user = user(UserRole.ADMIN, passwordEncoder.encode("strong-pass"));
        user.setStatus(UserStatus.DISABLED);
        when(appUserMapper.findByWebUsername("admin")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login("admin", "strong-pass"))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getStatus().value()).isEqualTo(401));
    }

    @Test
    void userWithNullPasswordHashIsRejected() {
        AppUser user = user(UserRole.ADMIN, null);
        when(appUserMapper.findByWebUsername("admin")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login("admin", "any-password"))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getStatus().value()).isEqualTo(401));
    }

    @Test
    void unknownUsernameReturnsUnauthorized() {
        when(appUserMapper.findByWebUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("unknown", "any-password"))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getStatus().value()).isEqualTo(401));
    }

    private AppUser user(UserRole role, String passwordHash) {
        AppUser user = new AppUser();
        user.setId(11L);
        user.setNickname("测试工作人员");
        user.setWebUsername("test-user");
        user.setWebPasswordHash(passwordHash);
        user.setRoleCode(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}

