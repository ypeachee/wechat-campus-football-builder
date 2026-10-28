package com.campusfootball;

import com.campusfootball.common.api.AppException;
import com.campusfootball.team.Team;
import com.campusfootball.team.TeamMapper;
import com.campusfootball.team.TeamQrCodeClient;
import com.campusfootball.team.TeamQrCodeService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamQrCodeServiceTest {
    private final TeamMapper teamMapper = mock(TeamMapper.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T08:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void cachesGeneratedCode() {
        Team team = new Team();
        team.setId(3L);
        when(teamMapper.findById(3L)).thenReturn(Optional.of(team));
        AtomicInteger calls = new AtomicInteger();
        TeamQrCodeClient client = teamId -> {
            calls.incrementAndGet();
            return new byte[]{1, 2, (byte) teamId};
        };
        TeamQrCodeService service = new TeamQrCodeService(teamMapper, client, clock);

        assertThat(service.get(3L)).containsExactly(1, 2, 3);
        assertThat(service.get(3L)).containsExactly(1, 2, 3);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void rejectsUnknownTeam() {
        TeamQrCodeService service = new TeamQrCodeService(
                teamMapper, teamId -> new byte[]{1}, clock);

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(AppException.class)
                .hasMessage("球队不存在");
    }
}