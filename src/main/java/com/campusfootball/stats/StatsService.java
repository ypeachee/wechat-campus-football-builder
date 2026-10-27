package com.campusfootball.stats;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StatsService {
    private final StatsMapper statsMapper;

    public StatsService(StatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    public List<ScorerRow> scorers(long seasonId) {
        return statsMapper.scorers(seasonId);
    }

    public List<AssistRow> assists(long seasonId) {
        return statsMapper.assists(seasonId);
    }

    public List<AppearanceRow> appearances(long seasonId) {
        return statsMapper.appearances(seasonId);
    }

    public List<StandingRow> standings(long seasonId) {
        return statsMapper.standings(seasonId);
    }
}
