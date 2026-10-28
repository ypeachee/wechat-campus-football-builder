package com.campusfootball;

import com.campusfootball.auth.AdminBootstrap;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class TestAuthSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String[] DELETE_ORDER = {
            "app_notification", "audit_log", "player_suspension_serving", "player_suspension", "fixture_event", "fixture_appearance", "fixture", "season_team", "season",
            "team_manager", "player", "team", "app_user"
    };

    private TestAuthSupport() {
    }

    public static JsonNode login(MockMvc mockMvc, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/wechat-login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    }

    public static String loginToken(MockMvc mockMvc, String code) throws Exception {
        return login(mockMvc, code).get("token").asText();
    }

    public static void resetUsers(JdbcTemplate jdbc, AdminBootstrap adminBootstrap) {
        for (String table : DELETE_ORDER) {
            jdbc.update("DELETE FROM " + table);
        }
        adminBootstrap.ensureAdmin();
    }
}
