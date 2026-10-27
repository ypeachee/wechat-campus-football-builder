package com.campusfootball.auth;

public interface WechatAuthClient {
    WechatIdentity exchange(String code);
}
