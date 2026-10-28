package com.campusfootball.notification;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NotificationMapper {
    int insertIgnore(@Param("userId") long userId,
                     @Param("type") NotificationType type,
                     @Param("title") String title,
                     @Param("content") String content,
                     @Param("linkUrl") String linkUrl,
                     @Param("dedupeKey") String dedupeKey);

    List<NotificationItem> findRecent(@Param("userId") long userId,
                                      @Param("limit") int limit,
                                      @Param("offset") int offset);

    int countUnread(@Param("userId") long userId);

    int markRead(@Param("userId") long userId, @Param("id") long id);

    int markAllRead(@Param("userId") long userId);

    List<Long> findAdminUserIds();

    List<Long> findTeamStaffUserIds(@Param("teamId") long teamId);

    List<UpcomingFixtureRow> findUpcomingFixturesForUser(@Param("userId") long userId,
                                                          @Param("from") LocalDateTime from,
                                                          @Param("until") LocalDateTime until);
}