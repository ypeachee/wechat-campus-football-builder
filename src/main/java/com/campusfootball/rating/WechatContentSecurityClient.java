package com.campusfootball.rating;

public interface WechatContentSecurityClient {
    ModerationDecision check(String openid, String content);
}
