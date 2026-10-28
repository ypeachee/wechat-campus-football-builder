package com.campusfootball;

import com.campusfootball.competition.FixtureStatus;
import com.campusfootball.competition.SeasonStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardIntegrationTest extends IntegrationTestBase {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void dashboardReturnsScopedOperationalCounts() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long seasonId = TestApiSupport.createSeason(mockMvc, admin, "DashboardSeason", SeasonStatus.ACTIVE.name());
        long home = TestApiSupport.createTeam(mockMvc, admin, "DashboardHome");
        long away = TestApiSupport.createTeam(mockMvc, admin, "DashboardAway");
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, home);
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, away);
        long fixtureId = TestApiSupport.createFixture(mockMvc, admin, seasonId, home, away);
        TestApiSupport.changeStatus(mockMvc, admin, fixtureId, FixtureStatus.SCHEDULED.name());
        jdbc.update("UPDATE fixture SET kickoff_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDate.now().plusDays(1).atTime(18, 0)), fixtureId);
        long playerId = TestApiSupport.createPlayer(mockMvc, admin, home, "DashboardPlayer", 18);
        mockMvc.perform(post("/api/v1/manage/suspensions")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"playerId\":" + playerId + ",\"seasonId\":" + seasonId
                                + ",\"matchesTotal\":1,\"reason\":\"测试停赛\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/manage/dashboard")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingRatings").value(0))
                .andExpect(jsonPath("$.reportedRatings").value(0))
                .andExpect(jsonPath("$.tomorrowFixtures").value(1))
                .andExpect(jsonPath("$.activeSuspensions").value(1));

        JsonNode manager = TestAuthSupport.login(mockMvc, "manager-code");
        mockMvc.perform(post("/api/v1/admin/teams/" + home + "/managers")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + manager.get("userId").asLong() + ",\"role\":\"TEAM_MANAGER\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/manage/dashboard")
                        .header("Authorization", "Bearer " + manager.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingRatings").doesNotExist())
                .andExpect(jsonPath("$.reportedRatings").doesNotExist())
                .andExpect(jsonPath("$.tomorrowFixtures").value(1));
    }
}