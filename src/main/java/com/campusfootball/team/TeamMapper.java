package com.campusfootball.team;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TeamMapper {
    List<Team> findActive();

    List<Team> findAll();

    Optional<Team> findById(@Param("id") long id);

    Optional<Team> findFirstByName(@Param("name") String name);

    int insert(Team team);

    int update(Team team);
}
