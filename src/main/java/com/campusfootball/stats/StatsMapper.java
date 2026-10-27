package com.campusfootball.stats;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StatsMapper {
    List<ScorerRow> scorers(@Param("seasonId") long seasonId);

    List<AssistRow> assists(@Param("seasonId") long seasonId);

    List<AppearanceRow> appearances(@Param("seasonId") long seasonId);

    List<StandingRow> standings(@Param("seasonId") long seasonId);
}
