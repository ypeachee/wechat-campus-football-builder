package com.campusfootball.competition;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface GroupMapper {
    int insert(CompetitionGroup group);
    Optional<CompetitionGroup> findById(@Param("id") long id);
    List<CompetitionGroup> findByStage(@Param("stageId") long stageId);
    int countGroupTeam(@Param("groupId") long groupId, @Param("teamId") long teamId);
    int countTeamInStage(@Param("stageId") long stageId, @Param("teamId") long teamId);
    int addTeam(@Param("groupId") long groupId, @Param("teamId") long teamId, @Param("seedNo") Integer seedNo);
    int removeTeam(@Param("groupId") long groupId, @Param("teamId") long teamId);
    List<GroupTeam> findTeams(@Param("groupId") long groupId);
    List<Long> findTeamIds(@Param("groupId") long groupId);
}
