package com.campusfootball;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnnouncementIntegrationTest extends IntegrationTestBase {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCanSendAnnouncementAndReadCountIsUpdated() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        JsonNode viewer = TestAuthSupport.login(mockMvc, "viewer-code");

        JsonNode created = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(
                mockMvc.perform(post("/api/v1/admin/announcements")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(APPLICATION_JSON)
                                .content("{\"title\":\"训练通知\",\"content\":\"周五训练照常\",\"audience\":\"USER\",\"targetUserId\":"
                                        + viewer.get("userId").asLong() + "}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.deliveredCount").value(1))
                        .andExpect(jsonPath("$.recipientCount").value(1))
                        .andReturn().getResponse().getContentAsString());
        long announcementId = created.get("id").asLong();

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + viewer.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("SYSTEM"))
                .andExpect(jsonPath("$.items[0].title").value("训练通知"))
                .andExpect(jsonPath("$.unreadCount").value(1));

        long notificationId = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(
                mockMvc.perform(get("/api/v1/notifications")
                                .header("Authorization", "Bearer " + viewer.get("token").asText()))
                        .andReturn().getResponse().getContentAsString()).get("items").get(0).get("id").asLong();
        mockMvc.perform(put("/api/v1/notifications/" + notificationId + "/read")
                        .header("Authorization", "Bearer " + viewer.get("token").asText()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/announcements/" + announcementId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readCount").value(1))
                .andExpect(jsonPath("$.deliveredCount").value(1));
    }

    @Test
    void teamManagerCanOnlyTargetTheirTeam() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = TestApiSupport.createTeam(mockMvc, admin, "AnnouncementTeam");
        JsonNode manager = TestAuthSupport.login(mockMvc, "manager-code");
        mockMvc.perform(post("/api/v1/admin/teams/" + teamId + "/managers")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + manager.get("userId").asLong() + ",\"role\":\"TEAM_MANAGER\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/announcements")
                        .header("Authorization", "Bearer " + manager.get("token").asText())
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"全站\",\"content\":\"不应允许\",\"audience\":\"ALL\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/announcements")
                        .header("Authorization", "Bearer " + manager.get("token").asText())
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"球队通知\",\"content\":\"周末集合\",\"audience\":\"TEAM\",\"targetTeamId\":" + teamId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientCount").value(1));
    }
}