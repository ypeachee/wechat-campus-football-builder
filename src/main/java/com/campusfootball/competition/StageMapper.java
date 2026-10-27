package com.campusfootball.competition;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface StageMapper {
    int insert(Stage stage);
    int update(Stage stage);
    Optional<Stage> findById(@Param("id") long id);
    List<Stage> findBySeason(@Param("seasonId") long seasonId);
    Optional<Stage> findFirstLeagueStage(@Param("seasonId") long seasonId);
    Optional<Stage> findFirstByType(@Param("seasonId") long seasonId, @Param("type") StageType type);
}
