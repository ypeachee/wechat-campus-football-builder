package com.campusfootball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OwnGoalStatsIntegrationTest extends IntegrationTestBase {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void ownGoalCreditsTeamButDoesNotIncreasePlayerGoals() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long ownTeam = TestApiSupport.createTeam(mockMvc, admin, "Own Goal FC");
        long beneficiaryTeam = TestApiSupport.createTeam(mockMvc, admin, "Beneficiary FC");
        long seasonId = TestApiSupport.createSeason(mockMvc, admin, "Own Goal Season", "ACTIVE");
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, ownTeam);
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, beneficiaryTeam);
        long ownPlayer = TestApiSupport.createPlayer(mockMvc, admin, ownTeam, "Own Goal Player", 5);
        long otherPlayer = TestApiSupport.createPlayer(mockMvc, admin, beneficiaryTeam, "Other Player", 9);
        long fixtureId = TestApiSupport.createFixture(mockMvc, admin, seasonId, ownTeam, beneficiaryTeam);
        TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "SCHEDULED");
        TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "LIVE");
        TestApiSupport.addAppearance(mockMvc, admin, fixtureId, ownTeam, ownPlayer, true, 90);
        TestApiSupport.addAppearance(mockMvc, admin, fixtureId, beneficiaryTeam, otherPlayer, true, 90);
        TestApiSupport.addEvent(mockMvc, admin, fixtureId, beneficiaryTeam, ownPlayer, "OWN_GOAL", null, 12, 0);
        TestApiSupport.finish(mockMvc, admin, fixtureId, 0, 1);

        JsonNode scorers = JsonMapper.builder().build().readTree(
                mockMvc.perform(get("/api/v1/stats/scorers").param("seasonId", Long.toString(seasonId)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        assertThat(StreamSupport.stream(scorers.spliterator(), false)
                .noneMatch(row -> row.get("playerId").asLong() == ownPlayer)).isTrue();

        mockMvc.perform(get("/api/v1/players/" + ownPlayer + "/stats").param("seasonId", Long.toString(seasonId)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.goals").value(0))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.ownGoals").value(1));

        mockMvc.perform(get("/api/v1/fixtures/" + fixtureId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.events[0].eventType").value("OWN_GOAL"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.events[0].teamId").value(beneficiaryTeam));
    }
}
