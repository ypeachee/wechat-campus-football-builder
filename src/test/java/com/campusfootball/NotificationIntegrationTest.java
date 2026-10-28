package com.campusfootball;

import com.campusfootball.competition.FixtureStatus;
import com.campusfootball.competition.SeasonStatus;
import com.campusfootball.notification.ScheduledNotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationIntegrationTest extends IntegrationTestBase {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void teamStaffReceiveUpcomingFixtureRemindersAndCanMarkThemRead() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long seasonId = TestApiSupport.createSeason(mockMvc, admin, "NotificationSeason", SeasonStatus.ACTIVE.name());
        long home = TestApiSupport.createTeam(mockMvc, admin, "NotificationHome");
        long away = TestApiSupport.createTeam(mockMvc, admin, "NotificationAway");
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, home);
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, away);
        long fixtureId = TestApiSupport.createFixture(mockMvc, admin, seasonId, home, away);
        TestApiSupport.changeStatus(mockMvc, admin, fixtureId, FixtureStatus.SCHEDULED.name());
        jdbc.update("UPDATE fixture SET kickoff_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().plusHours(2)), fixtureId);

        JsonNode leader = TestAuthSupport.login(mockMvc, "leader-code");
        long leaderId = leader.get("userId").asLong();
        mockMvc.perform(post("/api/v1/admin/teams/" + home + "/managers")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + leaderId + ",\"role\":\"TEAM_LEADER\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + leader.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.items[0].read").value(false));

        mockMvc.perform(put("/api/v1/notifications/read-all")
                        .header("Authorization", "Bearer " + leader.get("token").asText()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + leader.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        JsonNode viewer = TestAuthSupport.login(mockMvc, "viewer-code");
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + viewer.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_notification WHERE user_id = ?",
                Integer.class, leaderId)).isEqualTo(2);
    }
}