package com.campusfootball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditQueryIntegrationTest extends IntegrationTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void onlyAdminCanReadAuditLogs() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long seasonId = TestApiSupport.createSeason(mockMvc, admin, "AuditScopeSeason", "ACTIVE");
        long team1 = TestApiSupport.createTeam(mockMvc, admin, "AuditScopeTeam1");
        long team2 = TestApiSupport.createTeam(mockMvc, admin, "AuditScopeTeam2");
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, team1);
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, team2);
        TestApiSupport.createFixture(mockMvc, admin, seasonId, team1, team2);

        JsonNode managerLogin = TestAuthSupport.login(mockMvc, "manager-code");
        mockMvc.perform(post("/api/v1/admin/teams/" + team1 + "/managers")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + managerLogin.get("userId").asLong() + ",\"role\":\"TEAM_MANAGER\"}"))
                .andExpect(status().isOk());

        JsonNode adminLogs = getLogs(admin);
        assertThat(adminLogs.size()).isGreaterThan(0);
        assertThat(hasLog(adminLogs, "FIXTURE", "CREATE")).isTrue();

        mockMvc.perform(get("/api/v1/manage/audit-logs")
                        .header("Authorization", "Bearer " + managerLogin.get("token").asText()))
                .andExpect(status().isForbidden());

        String viewer = TestAuthSupport.loginToken(mockMvc, "viewer-code");
        mockMvc.perform(get("/api/v1/manage/audit-logs").header("Authorization", "Bearer " + viewer))
                .andExpect(status().isForbidden());
    }

    private JsonNode getLogs(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/manage/audit-logs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    }

    private boolean hasLog(JsonNode logs, String entityType, String action) {
        for (JsonNode log : logs) {
            if (entityType.equals(log.get("entityType").asText())
                    && action.equals(log.get("action").asText())) {
                return true;
            }
        }
        return false;
    }
}
