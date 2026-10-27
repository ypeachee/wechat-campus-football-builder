package com.campusfootball.competition;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface EventMapper {
    int insert(FixtureEvent event);

    Optional<FixtureEvent> findById(@Param("id") long id);

    int update(@Param("id") long id,
               @Param("playerId") long playerId,
               @Param("eventType") EventType eventType,
               @Param("relatedPlayerId") Long relatedPlayerId,
               @Param("minute") int minute,
               @Param("stoppageMinute") int stoppageMinute,
               @Param("note") String note);

    int delete(@Param("id") long id);

    java.util.List<FixtureEvent> findByFixtureId(@Param("fixtureId") long fixtureId);
}
