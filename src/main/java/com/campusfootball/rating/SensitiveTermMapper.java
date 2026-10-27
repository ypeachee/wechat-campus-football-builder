package com.campusfootball.rating;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SensitiveTermMapper {
    List<SensitiveTerm> findAll();
    List<SensitiveTerm> findEnabled();
    Optional<SensitiveTerm> findById(@Param("id") long id);
    Optional<SensitiveTerm> findByTerm(@Param("term") String term);
    int insert(SensitiveTerm term);
    int update(SensitiveTerm term);
    int delete(@Param("id") long id);
}
