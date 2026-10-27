package com.campusfootball.user;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface AppUserMapper {
    Optional<AppUser> findByOpenId(@Param("openid") String openid);

    Optional<AppUser> findById(@Param("id") long id);

    int insert(AppUser user);

    int updateRole(@Param("id") long id, @Param("role") UserRole role);

    int updateProfile(@Param("id") long id,
                      @Param("unionid") String unionid,
                      @Param("nickname") String nickname,
                      @Param("avatarUrl") String avatarUrl);
}
