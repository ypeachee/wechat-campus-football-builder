package com.campusfootball.rating;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface RatingMapper {
    Optional<PlayerMatchRating> findById(@Param("id") long id);
    Optional<PlayerMatchRating> findByTarget(@Param("fixtureId") long fixtureId,
                                             @Param("playerId") long playerId,
                                             @Param("userId") long userId);
    Optional<Long> findAppearanceTeamId(@Param("fixtureId") long fixtureId,
                                        @Param("playerId") long playerId);
    int countRecentSubmissions(@Param("userId") long userId,
                               @Param("since") LocalDateTime since);
    int insert(PlayerMatchRating rating);
    int updateSubmission(PlayerMatchRating rating);
    int countRecentReports(@Param("userId") long userId, @Param("since") LocalDateTime since);
    int insertReport(RatingReport report);
}
