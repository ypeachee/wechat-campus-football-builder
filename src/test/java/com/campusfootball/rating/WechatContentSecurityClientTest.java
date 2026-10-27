package com.campusfootball.rating;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WechatContentSecurityClientTest {
    private static final String CHECK_URL = "https://api.weixin.qq.com/wxa/msg_sec_check?access_token=access-1";

    @Test
    void mapsWechatSuggestionsAndParsesTextPlain() {
        assertThat(checkWithSuggestion("pass")).isEqualTo(ModerationDecision.PASS);
        assertThat(checkWithSuggestion("review")).isEqualTo(ModerationDecision.REVIEW);
        assertThat(checkWithSuggestion("risky")).isEqualTo(ModerationDecision.BLOCK);
    }

    @Test
    void failsClosedWhenTokenCannotBeRead() {
        WechatAccessTokenService tokenService = mock(WechatAccessTokenService.class);
        when(tokenService.getAccessToken()).thenThrow(new IllegalStateException("not configured"));
        WechatContentSecurityClient client = new WechatContentSecurityClientImpl(
                RestClient.builder(), tokenService, new ObjectMapper());

        assertThat(client.check("openid", "评价内容")).isEqualTo(ModerationDecision.REVIEW);
    }

    @Test
    void failsClosedWhenWechatReturnsMalformedResponse() {
        WechatAccessTokenService tokenService = mock(WechatAccessTokenService.class);
        when(tokenService.getAccessToken()).thenReturn("access-1");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(CHECK_URL))
                .andRespond(withSuccess("not-json", MediaType.TEXT_PLAIN));
        WechatContentSecurityClient client = new WechatContentSecurityClientImpl(builder, tokenService, new ObjectMapper());

        assertThat(client.check("openid", "评价内容")).isEqualTo(ModerationDecision.REVIEW);
        server.verify();
    }

    private ModerationDecision checkWithSuggestion(String suggestion) {
        WechatAccessTokenService tokenService = mock(WechatAccessTokenService.class);
        when(tokenService.getAccessToken()).thenReturn("access-1");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(CHECK_URL))
                .andRespond(withSuccess("{\"errcode\":0,\"result\":{\"suggest\":\"" + suggestion + "\"}}",
                        MediaType.TEXT_PLAIN));
        WechatContentSecurityClient client = new WechatContentSecurityClientImpl(builder, tokenService, new ObjectMapper());
        ModerationDecision decision = client.check("openid", "评价内容");
        server.verify();
        return decision;
    }
}
