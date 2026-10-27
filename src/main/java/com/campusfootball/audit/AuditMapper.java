package com.campusfootball.audit;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuditMapper {
    int insert(AuditLog log);

    List<AuditLog> findAll();

    List<AuditLog> findForTeams(@Param("teamIds") List<Long> teamIds);
}
