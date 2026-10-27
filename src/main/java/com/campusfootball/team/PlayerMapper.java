package com.campusfootball.team;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PlayerMapper {
    List<Player> findActiveByTeamId(@Param("teamId") long teamId);

    List<Player> findByTeamId(@Param("teamId") long teamId);

    Optional<Player> findById(@Param("id") long id);

    int insert(Player player);

    int update(Player player);

    int countActiveJersey(@Param("teamId") long teamId,
                          @Param("jerseyNo") int jerseyNo,
                          @Param("excludeId") Long excludeId);
}
