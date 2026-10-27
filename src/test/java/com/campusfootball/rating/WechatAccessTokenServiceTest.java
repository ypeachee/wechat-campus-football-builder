package com.campusfootball.rating;

import com.campusfootball.config.WechatProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WechatAccessTokenServiceTest {
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token"
            + "?grant_type=client_credential&appid=test-app-id&secret=test-secret";

    @Test
    void fetchesAndReusesAccessTokenFromTextPlainResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"access-1\",\"expires_in\":7200}", MediaType.TEXT_PLAIN));
        Clock clock = Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"), ZoneOffset.UTC);
        WechatAccessTokenService service = new WechatAccessTokenService(
                builder, new WechatProperties("test-app-id", "test-secret"), new ObjectMapper(), clock);

        assertThat(service.getAccessToken()).isEqualTo("access-1");
        assertThat(service.getAccessToken()).isEqualTo("access-1");
        server.verify();
    }

    @Test
    void rejectsMissingWechatConfiguration() {
        WechatAccessTokenService service = new WechatAccessTokenService(
                RestClient.builder(), new WechatProperties("", ""), new ObjectMapper(), Clock.systemUTC());

        assertThatThrownBy(service::getAccessToken).isInstanceOf(IllegalStateException.class);
    }
}
