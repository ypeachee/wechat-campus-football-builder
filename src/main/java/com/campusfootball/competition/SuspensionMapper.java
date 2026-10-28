package com.campusfootball.competition;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SuspensionMapper {
    List<CardEventRow> findFinishedCardEventsBySeason(@Param("seasonId") long seasonId);

    List<Long> findSeasonIdsWithFinishedCards();

    List<PlayerSuspension> findBySeasonAndTeam(@Param("seasonId") long seasonId, @Param("teamId") long teamId);

    List<PlayerSuspension> findAutomaticBySeason(@Param("seasonId") long seasonId);

    List<PlayerSuspension> findBySourceEventId(@Param("sourceEventId") long sourceEventId);

    Optional<PlayerSuspension> findById(@Param("id") long id);

    int insert(PlayerSuspension suspension);

    int updateProgress(@Param("id") long id, @Param("matchesServed") int matchesServed,
                       @Param("status") SuspensionStatus status);

    int updateTotalAndStatus(@Param("id") long id, @Param("matchesTotal") int matchesTotal,
                             @Param("status") SuspensionStatus status);

    int delete(@Param("id") long id);

    int countServing(@Param("suspensionId") long suspensionId, @Param("fixtureId") long fixtureId);

    int insertServing(@Param("suspensionId") long suspensionId, @Param("fixtureId") long fixtureId);
}