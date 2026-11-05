package com.campusfootball.tactic;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TeamTacticMapper {
    List<TeamTactic> findByTeamId(@Param("teamId") long teamId);

    Optional<TeamTactic> findById(@Param("id") long id);

    Optional<TeamTactic> findByTeamIdAndName(@Param("teamId") long teamId, @Param("name") String name);

    int insert(TeamTactic tactic);

    int update(TeamTactic tactic);

    int deleteById(@Param("id") long id);
}
