package com.campusfootball.competition;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface FixtureMapper {
    int insert(Fixture fixture);

    Optional<Fixture> findById(@Param("id") long id);

    List<Fixture> findPublicBySeason(@Param("seasonId") long seasonId);

    List<Fixture> findAllBySeason(@Param("seasonId") long seasonId);

    int updateSchedule(@Param("id") long id,
                       @Param("seasonId") long seasonId,
                       @Param("roundNo") Integer roundNo,
                       @Param("homeTeamId") long homeTeamId,
                       @Param("awayTeamId") long awayTeamId,
                       @Param("kickoffAt") java.time.LocalDateTime kickoffAt,
                       @Param("venue") String venue);

    int updateStatus(@Param("id") long id, @Param("status") FixtureStatus status);

    int updateScore(@Param("id") long id, @Param("homeScore") int homeScore, @Param("awayScore") int awayScore);

    int finish(@Param("id") long id, @Param("homeScore") int homeScore, @Param("awayScore") int awayScore);

    int updateLock(@Param("id") long id, @Param("locked") boolean locked);

    int countEvents(@Param("fixtureId") long fixtureId);

    int countAppearances(@Param("fixtureId") long fixtureId);

    int countBySeasonAndTeam(@Param("seasonId") long seasonId, @Param("teamId") long teamId);
}
