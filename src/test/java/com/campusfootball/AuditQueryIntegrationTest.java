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
    void adminSeesAllAuditLogsAndTeamManagerSeesOnlyOwnScope() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long seasonId = TestApiSupport.createSeason(mockMvc, admin, "AuditScopeSeason", "ACTIVE");
        long team1 = TestApiSupport.createTeam(mockMvc, admin, "AuditScopeTeam1");
        long team2 = TestApiSupport.createTeam(mockMvc, admin, "AuditScopeTeam2");
        long team3 = TestApiSupport.createTeam(mockMvc, admin, "AuditScopeTeam3");
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, team1);
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, team3);
        long fixtureId = TestApiSupport.createFixture(mockMvc, admin, seasonId, team1, team3);
        long player1 = TestApiSupport.createPlayer(mockMvc, admin, team1, "AuditScopePlayer1", 9);
        TestApiSupport.createPlayer(mockMvc, admin, team2, "AuditScopePlayer2", 10);
        TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "SCHEDULED");
        TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "LIVE");
        TestApiSupport.addEvent(mockMvc, admin, fixtureId, team1, player1, "GOAL", null, 10, 0);

        JsonNode managerLogin = TestAuthSupport.login(mockMvc, "manager-code");
        long managerId = managerLogin.get("userId").asLong();
        String manager = managerLogin.get("token").asText();
        mockMvc.perform(post("/api/v1/admin/teams/" + team1 + "/managers")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + managerId + "}"))
                .andExpect(status().isOk());

        JsonNode adminLogs = getLogs(admin);
        JsonNode managerLogs = getLogs(manager);
        String viewer = TestAuthSupport.loginToken(mockMvc, "viewer-code");

        assertThat(adminLogs.size()).isGreaterThan(managerLogs.size());
        assertThat(hasLog(adminLogs, "SEASON", null, null)).isTrue();
        assertThat(hasLog(managerLogs, "SEASON", null, null)).isFalse();
        assertThat(hasLog(managerLogs, "FIXTURE", null, "CREATE")).isTrue();
        assertThat(hasLog(managerLogs, "PLAYER", team2, "CREATE")).isFalse();
        assertThat(hasLog(managerLogs, "PLAYER", team1, "CREATE")).isTrue();
        assertThat(hasLog(managerLogs, "EVENT", team1, "CREATE")).isTrue();

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

    private boolean hasLog(JsonNode logs, String entityType, Long teamId, String action) {
        for (JsonNode log : logs) {
            boolean typeMatches = entityType.equals(log.get("entityType").asText());
            JsonNode teamNode = log.get("teamId");
            boolean teamMatches = teamId == null
                    ? teamNode == null || teamNode.isNull()
                    : teamNode != null && teamNode.asLong() == teamId;
            boolean actionMatches = action == null || action.equals(log.get("action").asText());
            if (typeMatches && teamMatches && actionMatches) {
                return true;
            }
        }
        return false;
    }
}
