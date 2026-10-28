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

class AuthorizedAdminIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void authorizedAdminCanUseGlobalManagementApis() throws Exception {
        Setup setup = setup();

        mockMvc.perform(get("/api/v1/admin/teams")
                        .header("Authorization", "Bearer " + setup.managerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/teams")
                        .header("Authorization", "Bearer " + setup.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"ManagerCreatedTeam\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/competitions")
                        .header("Authorization", "Bearer " + setup.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Manager Cup\",\"type\":\"CUP\",\"description\":\"managed by authorized admin\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/admin/teams/" + setup.teamId())
                        .header("Authorization", "Bearer " + setup.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"ManagerEditedTeam\",\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void authorizedAdminCannotAccessAuditRatingOrSensitiveTerms() throws Exception {
        Setup setup = setup();

        mockMvc.perform(get("/api/v1/manage/audit-logs")
                        .header("Authorization", "Bearer " + setup.managerToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/ratings?status=PENDING")
                        .header("Authorization", "Bearer " + setup.managerToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/sensitive-terms")
                        .header("Authorization", "Bearer " + setup.managerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorizedAdminCanGrantAnotherAuthorizedAdmin() throws Exception {
        Setup setup = setup();
        JsonNode target = TestAuthSupport.login(mockMvc, "manager-target");

        mockMvc.perform(post("/api/v1/admin/teams/" + setup.teamId() + "/managers")
                        .header("Authorization", "Bearer " + setup.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + target.get("userId").asLong() + ",\"role\":\"TEAM_MANAGER\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + target.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEAM_MANAGER"));
    }

    private Setup setup() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = TestApiSupport.createTeam(mockMvc, admin, "AuthorizedAdminTeam");
        JsonNode managerLogin = TestAuthSupport.login(mockMvc, "authorized-manager");
        mockMvc.perform(post("/api/v1/admin/teams/" + teamId + "/managers")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + managerLogin.get("userId").asLong() + ",\"role\":\"TEAM_MANAGER\"}"))
                .andExpect(status().isOk());
        return new Setup(teamId, managerLogin.get("token").asText());
    }

    private record Setup(long teamId, String managerToken) {
    }
}
