package com.campusfootball.rating;

import com.campusfootball.config.WechatProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;

@Service
public class WechatAccessTokenService {
    private static final Logger log = LoggerFactory.getLogger(WechatAccessTokenService.class);
    private final RestClient restClient;
    private final WechatProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private volatile CachedToken cachedToken;

    public WechatAccessTokenService(RestClient.Builder builder, WechatProperties properties,
                                    ObjectMapper objectMapper, Clock clock) {
        this.restClient = builder.baseUrl("https://api.weixin.qq.com").build();
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public String getAccessToken() {
        if (isBlank(properties.appId()) || isBlank(properties.appSecret())) {
            throw new IllegalStateException("WeChat content security is not configured");
        }
        CachedToken current = cachedToken;
        Instant now = clock.instant();
        if (current != null && current.expiresAt().isAfter(now)) {
            return current.token();
        }
        synchronized (this) {
            current = cachedToken;
            if (current != null && current.expiresAt().isAfter(now)) {
                return current.token();
            }
            CachedToken fetched = fetchToken();
            cachedToken = fetched;
            return fetched.token();
        }
    }

    private CachedToken fetchToken() {
        try {
            String body = restClient.get()
                    .uri(uri -> uri.path("/cgi-bin/token")
                            .queryParam("grant_type", "client_credential")
                            .queryParam("appid", properties.appId())
                            .queryParam("secret", properties.appSecret())
                            .build())
                    .retrieve()
                    .body(String.class);
            TokenResponse response = body == null || body.isBlank()
                    ? null : objectMapper.readValue(body, TokenResponse.class);
            if (response == null || response.access_token() == null || response.access_token().isBlank()) {
                if (response != null) {
                    log.warn("WeChat access token rejected: errcode={} errmsg={}", response.errcode(), response.errmsg());
                }
                throw new IllegalStateException("WeChat access token response is invalid");
            }
            int expiresIn = response.expires_in() == null ? 7200 : response.expires_in();
            long cacheSeconds = Math.max(60, expiresIn - 300L);
            return new CachedToken(response.access_token(), clock.instant().plusSeconds(cacheSeconds));
        } catch (RestClientException | JsonProcessingException ex) {
            log.warn("WeChat access token request failed: {}", ex.getClass().getSimpleName());
            throw new IllegalStateException("Unable to obtain WeChat access token", ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record TokenResponse(String access_token, Integer expires_in, Integer errcode, String errmsg) {
    }

    private record CachedToken(String token, Instant expiresAt) {
    }
}
