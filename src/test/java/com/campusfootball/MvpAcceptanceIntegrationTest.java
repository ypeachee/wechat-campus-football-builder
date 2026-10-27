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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MvpAcceptanceIntegrationTest extends IntegrationTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void completeLeagueFlow() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long homeTeam = TestApiSupport.createTeam(mockMvc, admin, "AcceptanceHome");
        long awayTeam = TestApiSupport.createTeam(mockMvc, admin, "AcceptanceAway");
        long seasonId = TestApiSupport.createSeason(mockMvc, admin, "AcceptanceSeason", "ACTIVE");
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, homeTeam);
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, awayTeam);
        long fixtureId = TestApiSupport.createFixture(mockMvc, admin, seasonId, homeTeam, awayTeam);
        TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "SCHEDULED");
        TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "LIVE");

        JsonNode managerLogin = TestAuthSupport.login(mockMvc, "manager-code");
        long managerId = managerLogin.get("userId").asLong();
        String manager = managerLogin.get("token").asText();
        mockMvc.perform(post("/api/v1/admin/teams/" + homeTeam + "/managers")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + managerId + "}"))
                .andExpect(status().isOk());

        long scorer = TestApiSupport.createPlayer(mockMvc, manager, homeTeam, "AcceptanceScorer", 9);
        long assister = TestApiSupport.createPlayer(mockMvc, manager, homeTeam, "AcceptanceAssister", 8);
        TestApiSupport.addAppearance(mockMvc, manager, fixtureId, homeTeam, scorer, true, 90);
        TestApiSupport.addAppearance(mockMvc, manager, fixtureId, homeTeam, assister, true, 90);
        TestApiSupport.addEvent(mockMvc, manager, fixtureId, homeTeam, scorer, "GOAL", assister, 12, 0);
        TestApiSupport.addEvent(mockMvc, manager, fixtureId, homeTeam, scorer, "YELLOW_CARD", null, 30, 0);

        TestApiSupport.finish(mockMvc, admin, fixtureId, 1, 0);

        mockMvc.perform(get("/api/v1/stats/scorers").param("seasonId", Long.toString(seasonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playerId").value(scorer))
                .andExpect(jsonPath("$[0].goals").value(1));
        mockMvc.perform(get("/api/v1/stats/assists").param("seasonId", Long.toString(seasonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playerId").value(assister))
                .andExpect(jsonPath("$[0].assists").value(1));
        JsonNode appearances = getPublicJson("/api/v1/stats/appearances?seasonId=" + seasonId);
        assertThat(row(appearances, "playerId", scorer).get("appearances").asLong()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/standings").param("seasonId", Long.toString(seasonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamId").value(homeTeam))
                .andExpect(jsonPath("$[0].points").value(3));

        mockMvc.perform(post("/api/v1/manage/fixtures/" + fixtureId + "/events")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(APPLICATION_JSON)
                        .content("{\"teamId\":" + homeTeam + ",\"playerId\":" + scorer
                                + ",\"eventType\":\"GOAL\",\"minute\":50,\"stoppageMinute\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FIXTURE_LOCKED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        JsonNode auditLogs = getJson("/api/v1/manage/audit-logs", admin);
        assertThat(containsEntity(auditLogs, "EVENT")).isTrue();
        assertThat(containsEntity(auditLogs, "APPEARANCE")).isTrue();
        assertThat(containsEntity(auditLogs, "FIXTURE")).isTrue();
    }

    private JsonNode getPublicJson(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode row(JsonNode rows, String field, long id) {
        for (JsonNode row : rows) {
            if (row.get(field).asLong() == id) {
                return row;
            }
        }
        return null;
    }

    private JsonNode getJson(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    }

    private boolean containsEntity(JsonNode logs, String entityType) {
        for (JsonNode log : logs) {
            if (entityType.equals(log.get("entityType").asText())) {
                return true;
            }
        }
        return false;
    }
}
