package com.campusfootball.rating;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface RefereeRatingMapper {
    Optional<FixtureRefereeRating> findByTarget(@Param("fixtureId") long fixtureId,
                                                @Param("refereeName") String refereeName,
                                                @Param("userId") long userId);
    int countRecentSubmissions(@Param("userId") long userId,
                               @Param("since") LocalDateTime since);
    int insert(FixtureRefereeRating rating);
    int updateSubmission(FixtureRefereeRating rating);
    RefereeRatingSummary summary(@Param("fixtureId") long fixtureId,
                                 @Param("refereeName") String refereeName);
}
