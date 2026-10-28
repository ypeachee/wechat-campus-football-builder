package com.campusfootball.team;

import com.campusfootball.rating.WechatAccessTokenService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class WechatTeamQrCodeClient implements TeamQrCodeClient {
    private static final Logger log = LoggerFactory.getLogger(WechatTeamQrCodeClient.class);
    private final RestClient restClient;
    private final WechatAccessTokenService accessTokenService;
    private final ObjectMapper objectMapper;
    private final String envVersion;

    public WechatTeamQrCodeClient(RestClient.Builder builder, WechatAccessTokenService accessTokenService,
                                  ObjectMapper objectMapper,
                                  @Value("${app.wechat.code-env-version:release}") String envVersion) {
        this.restClient = builder.baseUrl("https://api.weixin.qq.com").build();
        this.accessTokenService = accessTokenService;
        this.objectMapper = objectMapper;
        this.envVersion = envVersion;
    }

    @Override
    public byte[] fetch(long teamId) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("scene", "t=" + teamId);
            body.put("page", "pages/team-detail/index");
            body.put("check_path", false);
            body.put("env_version", envVersion);
            body.put("width", 430);
            body.put("auto_color", false);
            body.put("is_hyaline", false);
            String payload = objectMapper.writeValueAsString(body);
            ResponseEntity<byte[]> response = restClient.post()
                    .uri(uri -> uri.path("/wxa/getwxacodeunlimit")
                            .queryParam("access_token", accessTokenService.getAccessToken())
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] bytes = response.getBody();
            MediaType contentType = response.getHeaders().getContentType();
            if (bytes == null || bytes.length == 0
                    || contentType == null || !"image".equalsIgnoreCase(contentType.getType())) {
                String detail = bytes == null ? "empty response" : objectMapper.readTree(bytes).toString();
                log.warn("WeChat QR response rejected: {}", detail);
                throw new IllegalStateException("WeChat QR response is invalid");
            }
            return bytes;
        } catch (RestClientException | IOException ex) {
            if (ex instanceof RestClientResponseException responseException) {
                log.warn("WeChat QR request rejected: status={} body={}", responseException.getStatusCode().value(), responseException.getResponseBodyAsString());
            } else {
                log.warn("WeChat QR request failed: {}", ex.getClass().getSimpleName());
            }
            throw new IllegalStateException("Unable to generate WeChat QR code", ex);
        }
    }
}