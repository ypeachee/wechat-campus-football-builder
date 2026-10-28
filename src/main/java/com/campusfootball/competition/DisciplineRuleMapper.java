package com.campusfootball.competition;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface DisciplineRuleMapper {
    Optional<DisciplineRule> findByCompetitionId(@Param("competitionId") long competitionId);

    Optional<DisciplineRule> findBySeasonId(@Param("seasonId") long seasonId);

    int update(@Param("rule") DisciplineRule rule);
}