package com.campusfootball.competition;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface CompetitionMapper {
    List<Competition> findActive();
    List<Competition> findAll();
    Optional<Competition> findById(@Param("id") long id);
    Optional<Competition> findFirstByType(@Param("type") CompetitionType type);
    int insert(Competition competition);
    int update(Competition competition);
}
