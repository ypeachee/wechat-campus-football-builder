package com.campusfootball;

import com.campusfootball.auth.AdminBootstrap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;

class SecurityIntegrationTest extends IntegrationTestBase {

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
    void protectedEndpointWithoutTokenReturns401Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void malformedBearerTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void validTokenReturnsCurrentUser() throws Exception {
        String token = TestAuthSupport.loginToken(mockMvc, "viewer-code");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"))
                .andExpect(jsonPath("$.userId").isNumber());
    }

    @Test
    void publicWebAppAndAssetsAreAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/assets/app.js"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/assets/styles.css"))
                .andExpect(status().isOk());
    }

    @Test
    void securityHeadersArePresentOnResponses() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        Matchers.allOf(
                                Matchers.containsString("default-src 'self'"),
                                Matchers.containsString("frame-ancestors 'none'"))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy",
                        Matchers.containsString("camera=()")));
    }
}
