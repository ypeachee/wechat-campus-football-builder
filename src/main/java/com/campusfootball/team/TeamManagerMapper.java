package com.campusfootball.team;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TeamManagerMapper {
    List<Long> findTeamIdsByUserId(@Param("userId") long userId);
}
