package com.campusfootball.announcement;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface AnnouncementMapper {
    int insert(Announcement announcement);

    int updateRecipientCount(@Param("id") long id, @Param("recipientCount") int recipientCount);

    Optional<Announcement> findById(@Param("id") long id);

    List<Announcement> findPage(@Param("createdBy") Long createdBy,
                                @Param("limit") int limit,
                                @Param("offset") int offset);

    long count(@Param("createdBy") Long createdBy);
}