package com.campusfootball.auth;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.config.WechatProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class WechatApiClient implements WechatAuthClient {
    private static final Logger log = LoggerFactory.getLogger(WechatApiClient.class);

    private final RestClient restClient;
    private final WechatProperties properties;
    private final ObjectMapper objectMapper;

    public WechatApiClient(RestClient.Builder builder, WechatProperties properties, ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl("https://api.weixin.qq.com").build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public WechatIdentity exchange(String code) {
        if (isBlank(properties.appId()) || isBlank(properties.appSecret())) {
            log.warn("WeChat login rejected because the app-id or app-secret is not configured");
            throw failure(null);
        }
        try {
            String body = restClient.get()
                    .uri(uri -> uri.path("/sns/jscode2session")
                            .queryParam("appid", properties.appId())
                            .queryParam("secret", properties.appSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(String.class);
            WechatSessionResponse response = body == null || body.isBlank()
                    ? null
                    : objectMapper.readValue(body, WechatSessionResponse.class);
            if (response == null) {
                log.warn("WeChat code2session returned an empty response");
                throw failure(null);
            }
            if (response.errcode() != null && response.errcode() != 0) {
                log.warn("WeChat code2session rejected login: errcode={}, errmsg={}",
                        response.errcode(), response.errmsg());
                throw failure(response.errcode());
            }
            if (isBlank(response.openid())) {
                log.warn("WeChat code2session returned no openid: errcode={}, errmsg={}",
                        response.errcode(), response.errmsg());
                throw failure(response.errcode());
            }
            return new WechatIdentity(response.openid(), response.unionid());
        } catch (RestClientException ex) {
            log.warn("WeChat code2session transport failed: {}", ex.getClass().getSimpleName());
            throw failure(null);
        } catch (JsonProcessingException ex) {
            log.warn("WeChat code2session returned an unreadable response");
            throw failure(null);
        }
    }

    private AppException failure(Integer errcode) {
        String message = switch (errcode == null ? 0 : errcode) {
            case 40029, 40163 -> "微信登录凭证已失效，请重新登录";
            case 40013 -> "微信 AppID 配置错误";
            case 40125 -> "微信 AppSecret 配置错误";
            case 40164 -> "服务器 IP 未加入微信白名单";
            case 45011 -> "微信登录请求过于频繁，请稍后重试";
            default -> "微信登录失败";
        };
        return new AppException(HttpStatus.UNAUTHORIZED, ErrorCode.WECHAT_LOGIN_FAILED, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record WechatSessionResponse(String openid, String unionid, Integer errcode, String errmsg) {
    }
}
