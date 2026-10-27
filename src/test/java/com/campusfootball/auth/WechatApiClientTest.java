package com.campusfootball.auth;

import com.campusfootball.common.api.AppException;
import com.campusfootball.config.WechatProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WechatApiClientTest {
    private static final String SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session"
            + "?appid=test-app-id&secret=test-secret&js_code=test-code&grant_type=authorization_code";

    @Test
    void parsesTextPlainCode2SessionResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(SESSION_URL))
                .andRespond(withSuccess("{\"openid\":\"openid-123\",\"unionid\":\"unionid-123\"}", MediaType.TEXT_PLAIN));

        WechatIdentity identity = client(builder).exchange("test-code");

        assertThat(identity.openid()).isEqualTo("openid-123");
        assertThat(identity.unionid()).isEqualTo("unionid-123");
        server.verify();
    }

    @Test
    void mapsInvalidCodeToRetryableLoginFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(SESSION_URL))
                .andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"invalid code\"}", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client(builder).exchange("test-code"))
                .isInstanceOf(AppException.class)
                .hasMessage("微信登录凭证已失效，请重新登录");
        server.verify();
    }

    private WechatApiClient client(RestClient.Builder builder) {
        return new WechatApiClient(builder, new WechatProperties("test-app-id", "test-secret"), new ObjectMapper());
    }
}
