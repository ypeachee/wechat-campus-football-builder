package com.campusfootball.competition;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface AppearanceMapper {
    int insert(FixtureAppearance appearance);

    Optional<FixtureAppearance> findById(@Param("id") long id);

    int countByFixtureAndPlayer(@Param("fixtureId") long fixtureId, @Param("playerId") long playerId);

    int update(@Param("id") long id,
               @Param("starter") boolean starter,
               @Param("minutesPlayed") Integer minutesPlayed);

    int delete(@Param("id") long id);

    java.util.List<FixtureAppearance> findByFixtureId(@Param("fixtureId") long fixtureId);
    java.util.List<FixtureAppearance> findByFixtureAndTeam(@Param("fixtureId") long fixtureId,
                                                           @Param("teamId") long teamId);
    int deleteByFixtureAndTeam(@Param("fixtureId") long fixtureId, @Param("teamId") long teamId);
    java.util.List<Long> findPlayerIdsWithEvents(@Param("fixtureId") long fixtureId,
                                                 @Param("teamId") long teamId);
}
