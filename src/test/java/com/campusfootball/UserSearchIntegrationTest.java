package com.campusfootball;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserSearchIntegrationTest extends IntegrationTestBase {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void managersCanSearchUsersButLeadersAndViewersCannot() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = TestApiSupport.createTeam(mockMvc, admin, "SearchTeam");
        JsonNode target = TestAuthSupport.login(mockMvc, "viewer-code");
        long targetId = target.get("userId").asLong();
        jdbc.update("UPDATE app_user SET nickname = 'SearchTarget' WHERE id = ?", targetId);

        mockMvc.perform(get("/api/v1/admin/users?query=SearchTarget")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(targetId))
                .andExpect(jsonPath("$[0].nickname").value("SearchTarget"));

        JsonNode manager = TestAuthSupport.login(mockMvc, "manager-code");
        mockMvc.perform(post("/api/v1/admin/teams/" + teamId + "/managers")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + manager.get("userId").asLong() + ",\"role\":\"TEAM_MANAGER\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/users?query=" + targetId)
                        .header("Authorization", "Bearer " + manager.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(targetId));

        JsonNode leader = TestAuthSupport.login(mockMvc, "leader-code");
        mockMvc.perform(post("/api/v1/admin/teams/" + teamId + "/managers")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + leader.get("userId").asLong() + ",\"role\":\"TEAM_LEADER\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + leader.get("token").asText()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + target.get("token").asText()))
                .andExpect(status().isForbidden());
    }
}
