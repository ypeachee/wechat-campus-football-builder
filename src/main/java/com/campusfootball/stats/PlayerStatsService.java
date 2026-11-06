package com.campusfootball.stats;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.competition.SeasonMapper;
import com.campusfootball.team.PlayerService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PlayerStatsService {
    private final StatsMapper statsMapper;
    private final PlayerService playerService;
    private final SeasonMapper seasonMapper;

    public PlayerStatsService(StatsMapper statsMapper, PlayerService playerService, SeasonMapper seasonMapper) {
        this.statsMapper = statsMapper;
        this.playerService = playerService;
        this.seasonMapper = seasonMapper;
    }

    public PlayerSeasonStats get(long playerId, long seasonId) {
        playerService.getActivePlayer(playerId);
        seasonMapper.findById(seasonId).orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "赛季不存在"));
        PlayerSeasonSummary summary = statsMapper.playerSeasonSummary(playerId, seasonId);
        if (summary == null) {
            throw new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "球员不存在");
        }
        List<PlayerMatchStat> matches = statsMapper.playerMatchStats(playerId, seasonId);
        return new PlayerSeasonStats(summary.playerId(), summary.seasonId(), summary.seasonName(),
                summary.appearances(), summary.starts(), summary.substitutes(), summary.minutesPlayed(),
                summary.goals(), summary.assists(), summary.yellowCards(), summary.redCards(), summary.ownGoals(),
                matches);
    }
}
