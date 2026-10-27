package com.campusfootball.auth;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.common.security.JwtService;
import com.campusfootball.common.security.UserPrincipal;
import com.campusfootball.team.TeamManagerMapper;
import com.campusfootball.user.AppUser;
import com.campusfootball.user.AppUserMapper;
import com.campusfootball.user.UserRole;
import com.campusfootball.user.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {
    private final WechatAuthClient wechatAuthClient;
    private final AppUserMapper appUserMapper;
    private final TeamManagerMapper teamManagerMapper;
    private final JwtService jwtService;

    public AuthService(WechatAuthClient wechatAuthClient,
                       AppUserMapper appUserMapper,
                       TeamManagerMapper teamManagerMapper,
                       JwtService jwtService) {
        this.wechatAuthClient = wechatAuthClient;
        this.appUserMapper = appUserMapper;
        this.teamManagerMapper = teamManagerMapper;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse login(String code) {
        WechatIdentity identity = wechatAuthClient.exchange(code);
        AppUser user = appUserMapper.findByOpenId(identity.openid())
                .orElseGet(() -> createViewer(identity));
        if (identity.unionid() != null && !identity.unionid().equals(user.getUnionid())) {
            appUserMapper.updateProfile(user.getId(), identity.unionid(), null, null);
            user.setUnionid(identity.unionid());
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "账号已停用");
        }
        return new AuthResponse(
                jwtService.createToken(user.getId()),
                user.getId(),
                user.getNickname(),
                user.getRoleCode(),
                teamManagerMapper.findTeamIdsByUserId(user.getId()));
    }

    public MeResponse me(UserPrincipal principal) {
        AppUser user = appUserMapper.findById(principal.userId())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "用户不存在"));
        return new MeResponse(
                user.getId(),
                user.getNickname(),
                user.getRoleCode(),
                teamManagerMapper.findTeamIdsByUserId(user.getId()));
    }

    private AppUser createViewer(WechatIdentity identity) {
        AppUser user = new AppUser();
        user.setWechatOpenid(identity.openid());
        user.setUnionid(identity.unionid());
        user.setRoleCode(UserRole.VIEWER);
        user.setStatus(UserStatus.ACTIVE);
        appUserMapper.insert(user);
        return user;
    }
}
