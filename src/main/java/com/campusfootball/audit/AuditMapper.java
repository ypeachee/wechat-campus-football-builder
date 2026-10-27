package com.campusfootball.audit;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditMapper {
    int insert(AuditLog log);
}
