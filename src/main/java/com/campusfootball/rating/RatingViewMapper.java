package com.campusfootball.rating;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RatingViewMapper {
    List<PlayerRatingSummary> fixtureSummaries(@Param("fixtureId") long fixtureId);
    PlayerSeasonRatingSummary playerSeasonSummary(@Param("playerId") long playerId,
                                                   @Param("seasonId") Long seasonId);
    long countComments(@Param("fixtureId") long fixtureId, @Param("playerId") long playerId);
    List<RatingCommentView> comments(@Param("fixtureId") long fixtureId,
                                     @Param("playerId") long playerId,
                                     @Param("limit") int limit,
                                     @Param("offset") int offset);
}
