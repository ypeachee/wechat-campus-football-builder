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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MultiCompetitionAcceptanceIntegrationTest extends IntegrationTestBase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void cupGroupsGenerateSingleLegBracketAndCrownChampion() throws Exception {
        // Keep IDs outside the JVM Long cache so boxed-ID comparisons are exercised consistently.
        jdbc.update("ALTER TABLE stage AUTO_INCREMENT = 1000");
        jdbc.update("ALTER TABLE competition_group AUTO_INCREMENT = 1000");
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long competitionId = postId("/api/v1/admin/competitions", admin,
                "{\"name\":\"CUGB Cup\",\"type\":\"CUP\"}");
        long seasonId = postId("/api/v1/admin/seasons", admin,
                "{\"competitionId\":" + competitionId + ",\"name\":\"2026 Cup\",\"status\":\"ACTIVE\"}");
        long groupStageId = postId("/api/v1/admin/stages", admin,
                "{\"seasonId\":" + seasonId + ",\"name\":\"小组赛\",\"type\":\"GROUP\",\"sortOrder\":1,\"status\":\"ACTIVE\"}");
        long knockoutStageId = postId("/api/v1/admin/stages", admin,
                "{\"seasonId\":" + seasonId + ",\"name\":\"淘汰赛\",\"type\":\"KNOCKOUT\",\"sortOrder\":2,\"status\":\"ACTIVE\"}");

        long teamA = TestApiSupport.createTeam(mockMvc, admin, "CupA");
        long teamB = TestApiSupport.createTeam(mockMvc, admin, "CupB");
        long teamC = TestApiSupport.createTeam(mockMvc, admin, "CupC");
        long teamD = TestApiSupport.createTeam(mockMvc, admin, "CupD");
        for (long team : new long[]{teamA, teamB, teamC, teamD}) {
            mockMvc.perform(post("/api/v1/admin/seasons/" + seasonId + "/teams/" + team)
                    .header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
        }
        long groupA = postId("/api/v1/admin/stages/" + groupStageId + "/groups", admin,
                "{\"name\":\"A组\",\"sortOrder\":1}");
        long groupB = postId("/api/v1/admin/stages/" + groupStageId + "/groups", admin,
                "{\"name\":\"B组\",\"sortOrder\":2}");
        addGroupTeam(admin, groupA, teamA);
        addGroupTeam(admin, groupA, teamB);
        addGroupTeam(admin, groupB, teamC);
        addGroupTeam(admin, groupB, teamD);

        finishFixture(admin, createFixture(admin, seasonId, groupStageId, groupA, teamA, teamB), 1, 0, null, null);
        finishFixture(admin, createFixture(admin, seasonId, groupStageId, groupB, teamC, teamD), 2, 1, null, null);

        JsonNode bracket = postJson("/api/v1/admin/stages/" + knockoutStageId + "/generate-bracket", admin,
                "{\"groupStageId\":" + groupStageId + ",\"qualifiersPerGroup\":2,\"thirdPlace\":false}");
        assertThat(bracket.get("rounds").size()).isEqualTo(2);
        assertThat(bracket.get("rounds").get(0).get("ties").size()).isEqualTo(2);

        long semi1 = bracket.get("rounds").get(0).get("ties").get(0).get("id").asLong();
        long semi2 = bracket.get("rounds").get(0).get("ties").get(1).get("id").asLong();
        long fixture1 = postId("/api/v1/admin/ties/" + semi1 + "/fixture", admin, "{}");
        long fixture2 = postId("/api/v1/admin/ties/" + semi2 + "/fixture", admin, "{}");
        finishFixture(admin, fixture1, 2, 0, null, null);
        finishFixture(admin, fixture2, 1, 0, null, null);

        JsonNode semiFinalBracket = getJson("/api/v1/brackets?stageId=" + knockoutStageId);
        long finalTieId = semiFinalBracket.get("rounds").get(1).get("ties").get(0).get("id").asLong();
        long finalFixture = postId("/api/v1/admin/ties/" + finalTieId + "/fixture", admin, "{}");
        finishFixture(admin, finalFixture, 1, 1, 4, 3);

        JsonNode finalBracket = getJson("/api/v1/brackets?stageId=" + knockoutStageId);
        assertThat(finalBracket.get("champion")).isNotNull();
        assertThat(finalBracket.get("champion").get("id").asLong()).isIn(teamA, teamC);
    }

    private long createFixture(String admin, long seasonId, long stageId, long groupId, long home, long away) throws Exception {
        return postId("/api/v1/admin/fixtures", admin, "{\"seasonId\":" + seasonId + ",\"stageId\":" + stageId
                + ",\"groupId\":" + groupId + ",\"homeTeamId\":" + home + ",\"awayTeamId\":" + away + "}");
    }

    private void addGroupTeam(String admin, long groupId, long teamId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/groups/" + groupId + "/teams")
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"teamId\":" + teamId + ",\"seedNo\":1}")).andExpect(status().isOk());
    }

    private void finishFixture(String admin, long fixtureId, int home, int away,
                               Integer homePenalty, Integer awayPenalty) throws Exception {
        mockMvc.perform(put("/api/v1/admin/fixtures/" + fixtureId + "/status")
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON).content("{\"status\":\"SCHEDULED\"}")).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/admin/fixtures/" + fixtureId + "/status")
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON).content("{\"status\":\"LIVE\"}")).andExpect(status().isOk());
        String penalties = homePenalty == null ? "" : ",\"homePenaltyScore\":" + homePenalty + ",\"awayPenaltyScore\":" + awayPenalty;
        mockMvc.perform(post("/api/v1/admin/fixtures/" + fixtureId + "/finish")
                .header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON)
                .content("{\"homeScore\":" + home + ",\"awayScore\":" + away + penalties + "}"))
                .andExpect(status().isOk());
    }

    private long postId(String path, String admin, String body) throws Exception {
        return postJson(path, admin, body).get("id").asLong();
    }

    private JsonNode postJson(String path, String admin, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(path).header("Authorization", "Bearer " + admin)
                .contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
        return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    }
}
