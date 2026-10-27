package com.campusfootball.stats;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StatsMapper {
    List<ScorerRow> scorers(@Param("filter") StatsFilter filter);

    List<AssistRow> assists(@Param("filter") StatsFilter filter);

    List<AppearanceRow> appearances(@Param("filter") StatsFilter filter);

    List<StandingRow> leagueStandings(@Param("filter") StatsFilter filter);

    List<StandingRow> groupStandings(@Param("filter") StatsFilter filter);
}
