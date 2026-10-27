package com.campusfootball.auth;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.config.WechatProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class WechatApiClient implements WechatAuthClient {
    private final RestClient restClient;
    private final WechatProperties properties;

    public WechatApiClient(RestClient.Builder builder, WechatProperties properties) {
        this.restClient = builder.baseUrl("https://api.weixin.qq.com").build();
        this.properties = properties;
    }

    @Override
    public WechatIdentity exchange(String code) {
        if (isBlank(properties.appId()) || isBlank(properties.appSecret())) {
            throw failure();
        }
        try {
            WechatSessionResponse response = restClient.get()
                    .uri(uri -> uri.path("/sns/jscode2session")
                            .queryParam("appid", properties.appId())
                            .queryParam("secret", properties.appSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(WechatSessionResponse.class);
            if (response == null || response.errcode() != null && response.errcode() != 0
                    || isBlank(response.openid())) {
                throw failure();
            }
            return new WechatIdentity(response.openid(), response.unionid());
        } catch (RestClientException ex) {
            throw failure();
        }
    }

    private AppException failure() {
        return new AppException(HttpStatus.UNAUTHORIZED, ErrorCode.WECHAT_LOGIN_FAILED, "微信登录失败");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record WechatSessionResponse(String openid, String unionid, Integer errcode, String errmsg) {
    }
}
