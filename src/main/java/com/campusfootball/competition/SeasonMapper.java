package com.campusfootball.competition;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.campusfootball.team.Team;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SeasonMapper {
    int insert(Season season);

    Optional<Season> findById(@Param("id") long id);

    List<Season> findPublic();
    List<Season> findPublicByCompetition(@Param("competitionId") long competitionId);

    List<Season> findAll();
    List<Season> findAllByCompetition(@Param("competitionId") long competitionId);

    int countSeasonTeam(@Param("seasonId") long seasonId, @Param("teamId") long teamId);

    int addTeam(@Param("seasonId") long seasonId, @Param("teamId") long teamId);

    int removeTeam(@Param("seasonId") long seasonId, @Param("teamId") long teamId);

    List<Team> findTeamsBySeason(@Param("seasonId") long seasonId);
}
