package com.campusfootball.rating;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class WechatContentSecurityClientImpl implements WechatContentSecurityClient {
    private static final Logger log = LoggerFactory.getLogger(WechatContentSecurityClientImpl.class);

    private final RestClient restClient;
    private final WechatAccessTokenService accessTokenService;
    private final ObjectMapper objectMapper;

    public WechatContentSecurityClientImpl(RestClient.Builder builder,
                                           WechatAccessTokenService accessTokenService,
                                           ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl("https://api.weixin.qq.com").build();
        this.accessTokenService = accessTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ModerationDecision check(String openid, String content) {
        if (content == null || content.isBlank()) {
            return ModerationDecision.PASS;
        }
        try {
            String accessToken = accessTokenService.getAccessToken();
            String body = restClient.post()
                    .uri(uri -> uri.path("/wxa/msg_sec_check")
                            .queryParam("access_token", accessToken)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", content, "version", 2, "scene", 2, "openid", openid))
                    .retrieve()
                    .body(String.class);
            ContentCheckResponse response = body == null || body.isBlank()
                    ? null : objectMapper.readValue(body, ContentCheckResponse.class);
            if (response == null || response.errcode() == null || response.errcode() != 0
                    || response.result() == null || response.result().suggest() == null) {
                log.warn("WeChat content security returned an invalid result: errcode={}",
                        response == null ? null : response.errcode());
                return ModerationDecision.REVIEW;
            }
            return switch (response.result().suggest()) {
                case "pass" -> ModerationDecision.PASS;
                case "review" -> ModerationDecision.REVIEW;
                case "risky" -> ModerationDecision.BLOCK;
                default -> ModerationDecision.REVIEW;
            };
        } catch (RestClientException | JsonProcessingException | IllegalStateException ex) {
            log.warn("WeChat content security failed closed: {}", ex.getClass().getSimpleName());
            return ModerationDecision.REVIEW;
        }
    }

    private record ContentCheckResponse(Integer errcode, String errmsg, Result result) {
    }

    private record Result(String suggest) {
    }
}
