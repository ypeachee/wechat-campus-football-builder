package com.campusfootball.team;

import com.campusfootball.user.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TeamManagerMapper {
    List<Long> findTeamIdsByUserId(@Param("userId") long userId);

    List<TeamManagerAssignment> findAssignmentsByTeamId(@Param("teamId") long teamId);

    List<UserRole> findRoleCodesByUserId(@Param("userId") long userId);

    UserRole findRoleByUserAndTeam(@Param("userId") long userId, @Param("teamId") long teamId);

    int countByUserAndTeam(@Param("userId") long userId, @Param("teamId") long teamId);

    int insertGrant(@Param("userId") long userId,
                    @Param("teamId") long teamId,
                    @Param("grantedBy") long grantedBy,
                    @Param("roleCode") UserRole roleCode);

    int deleteGrant(@Param("userId") long userId, @Param("teamId") long teamId);
}