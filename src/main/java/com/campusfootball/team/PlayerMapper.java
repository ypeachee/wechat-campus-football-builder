package com.campusfootball.team;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface PlayerMapper {
    List<Player> findActiveByTeamId(@Param("teamId") long teamId);

    List<Player> findByTeamId(@Param("teamId") long teamId);

    Optional<Player> findById(@Param("id") long id);

    Optional<Player> findActiveByTeamAndJersey(@Param("teamId") long teamId,
                                                @Param("jerseyNo") String jerseyNo);

    int insert(Player player);

    int update(Player player);

    int countActiveJersey(@Param("teamId") long teamId,
                          @Param("jerseyNo") String jerseyNo,
                          @Param("excludeId") Long excludeId);

    int updatePhoto(@Param("id") long id, @Param("imageData") byte[] imageData,
                    @Param("imageContentType") String imageContentType,
                    @Param("imageFilename") String imageFilename);

    int clearPhoto(@Param("id") long id);

    Optional<PlayerPhoto> findPhotoById(@Param("id") long id);
}
