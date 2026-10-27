package com.campusfootball;

import com.campusfootball.auth.AdminBootstrap;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdminBootstrap adminBootstrap;

    @BeforeEach
    void resetData() {
        TestAuthSupport.resetUsers(jdbc, adminBootstrap);
    }

    @Test
    void createsViewerOnFirstLoginAndReusesUserOnSecondLogin() throws Exception {
        JsonNode first = TestAuthSupport.login(mockMvc, "viewer-code");
        JsonNode second = TestAuthSupport.login(mockMvc, "viewer-code");

        assertThat(first.get("role").asText()).isEqualTo("VIEWER");
        assertThat(second.get("userId").asLong()).isEqualTo(first.get("userId").asLong());
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE wechat_openid = 'openid-viewer'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void bootstrapOpenIdHasAdminRole() throws Exception {
        JsonNode response = TestAuthSupport.login(mockMvc, "admin-code");

        assertThat(response.get("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    void roleChangeIsVisibleBeforeTokenExpiry() throws Exception {
        JsonNode login = TestAuthSupport.login(mockMvc, "viewer-code");
        long userId = login.get("userId").asLong();
        String token = login.get("token").asText();

        jdbc.update("UPDATE app_user SET role_code = 'TEAM_MANAGER' WHERE id = ?", userId);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEAM_MANAGER"));
    }

    @Test
    void disabledUserReceivesUnauthorized() throws Exception {
        JsonNode login = TestAuthSupport.login(mockMvc, "viewer-code");
        long userId = login.get("userId").asLong();
        String token = login.get("token").asText();

        jdbc.update("UPDATE app_user SET status = 'DISABLED' WHERE id = ?", userId);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void unknownCodeReturnsWechatLoginFailure() throws Exception {
        mockMvc.perform(post("/api/v1/auth/wechat-login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"unknown-code\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WECHAT_LOGIN_FAILED"));
    }
}
