package com.campusfootball.dashboard;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface DashboardMapper {
    long countTomorrowFixtures(@Param("userId") long userId,
                               @Param("allTeams") boolean allTeams,
                               @Param("from") LocalDateTime from,
                               @Param("until") LocalDateTime until);

    long countActiveSuspensions(@Param("userId") long userId,
                                @Param("allTeams") boolean allTeams);
}