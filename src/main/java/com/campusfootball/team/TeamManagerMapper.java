package com.campusfootball.team;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TeamManagerMapper {
    List<Long> findTeamIdsByUserId(@Param("userId") long userId);

    int countByUserAndTeam(@Param("userId") long userId, @Param("teamId") long teamId);

    int countByUserId(@Param("userId") long userId);

    int insertGrant(@Param("userId") long userId,
                    @Param("teamId") long teamId,
                    @Param("grantedBy") long grantedBy);

    int deleteGrant(@Param("userId") long userId, @Param("teamId") long teamId);
}
