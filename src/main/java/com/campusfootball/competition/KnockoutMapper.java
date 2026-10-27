package com.campusfootball.competition;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface KnockoutMapper {
    int insertRound(KnockoutRound round);
    List<KnockoutRound> findRounds(@Param("stageId") long stageId);
    Optional<KnockoutRound> findRoundById(@Param("id") long id);

    int insertTie(KnockoutTie tie);
    int updateTie(KnockoutTie tie);
    Optional<KnockoutTie> findTieById(@Param("id") long id);
    List<KnockoutTie> findTies(@Param("stageId") long stageId);
    List<KnockoutTie> findFirstRoundTies(@Param("stageId") long stageId);

    int insertSeed(KnockoutSeed seed);
    int deleteSeeds(@Param("stageId") long stageId);
    List<KnockoutSeed> findSeeds(@Param("stageId") long stageId);
}
