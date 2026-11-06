package com.campusfootball.news;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface SiteNewsMapper {
    int insert(SiteNews news);
    int update(SiteNews news);
    int delete(@Param("id") long id);
    Optional<SiteNews> findById(@Param("id") long id);
    List<SiteNews> findPublic(@Param("limit") int limit);
    List<SiteNews> findAll();
    long countAll();
}
