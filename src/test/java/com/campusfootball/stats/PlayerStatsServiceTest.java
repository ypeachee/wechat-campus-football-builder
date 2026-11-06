package com.campusfootball.stats;

import com.campusfootball.common.api.AppException;
import com.campusfootball.competition.Season;
import com.campusfootball.competition.SeasonMapper;
import com.campusfootball.team.Player;
import com.campusfootball.team.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerStatsServiceTest {
    private final StatsMapper statsMapper = mock(StatsMapper.class);
    private final PlayerService playerService = mock(PlayerService.class);
    private final SeasonMapper seasonMapper = mock(SeasonMapper.class);
    private PlayerStatsService service;

    @BeforeEach
    void setUp() {
        service = new PlayerStatsService(statsMapper, playerService, seasonMapper);
    }

    @Test
    void returnsSeasonSummaryAndMatchBreakdown() {
        Player player = new Player();
        player.setId(501L);
        Season season = new Season();
        season.setId(5L);
        season.setName("2026秋六队足球邀请赛");
        PlayerSeasonSummary summary = new PlayerSeasonSummary(501L, 5L, season.getName(),
                4L, 3L, 1L, 286L, 2L, 3L, 1L, 0L, 0L);
        PlayerMatchStat match = new PlayerMatchStat(19L, 1, LocalDateTime.of(2026, 11, 1, 15, 0),
                10L, "地信二", 5, 0, true, 90, 2L, 1L, 0L, 0L, 0L);
        when(playerService.getActivePlayer(501L)).thenReturn(player);
        when(seasonMapper.findById(5L)).thenReturn(Optional.of(season));
        when(statsMapper.playerSeasonSummary(501L, 5L)).thenReturn(summary);
        when(statsMapper.playerMatchStats(501L, 5L)).thenReturn(List.of(match));

        PlayerSeasonStats result = service.get(501L, 5L);

        assertThat(result.playerId()).isEqualTo(501L);
        assertThat(result.seasonName()).isEqualTo("2026秋六队足球邀请赛");
        assertThat(result.appearances()).isEqualTo(4L);
        assertThat(result.minutesPlayed()).isEqualTo(286L);
        assertThat(result.goals()).isEqualTo(2L);
        assertThat(result.assists()).isEqualTo(3L);
        assertThat(result.yellowCards()).isEqualTo(1L);
        assertThat(result.matches()).containsExactly(match);
    }

    @Test
    void unknownSeasonIsRejected() {
        Player player = new Player();
        player.setId(501L);
        when(playerService.getActivePlayer(501L)).thenReturn(player);
        when(seasonMapper.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(501L, 404L))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getStatus().value()).isEqualTo(404));
    }
}
