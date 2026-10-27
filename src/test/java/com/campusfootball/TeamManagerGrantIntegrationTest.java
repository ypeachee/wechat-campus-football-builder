package com.campusfootball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamManagerGrantIntegrationTest extends IntegrationTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void grantPromotesViewerAndRevokeLastGrantDemotesToViewer() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = createTeam(admin, "GrantTeam");
        JsonNode managerLogin = TestAuthSupport.login(mockMvc, "manager-code");
        long managerId = managerLogin.get("userId").asLong();
        String manager = managerLogin.get("token").asText();

        grant(admin, teamId, managerId);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEAM_MANAGER"))
                .andExpect(jsonPath("$.managedTeamIds[0]").value(teamId));

        revoke(admin, teamId, managerId);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"))
                .andExpect(jsonPath("$.managedTeamIds").isEmpty());

        Integer auditRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE team_id = ? AND entity_type = 'TEAM_MANAGER'",
                Integer.class, teamId);
        assertThat(auditRows).isEqualTo(2);
    }

    @Test
    void adminCanListCurrentTeamManagers() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = createTeam(admin, "ManagerListTeam");
        JsonNode managerLogin = TestAuthSupport.login(mockMvc, "manager-code");
        long managerId = managerLogin.get("userId").asLong();
        grant(admin, teamId, managerId);

        mockMvc.perform(get("/api/v1/admin/teams/" + teamId + "/managers")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(managerId));

        revoke(admin, teamId, managerId);
        mockMvc.perform(get("/api/v1/admin/teams/" + teamId + "/managers")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void alreadyIssuedTokenLosesAccessAfterRevocation() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = createTeam(admin, "RevokeTeam");
        JsonNode managerLogin = TestAuthSupport.login(mockMvc, "manager-code");
        long managerId = managerLogin.get("userId").asLong();
        String manager = managerLogin.get("token").asText();
        grant(admin, teamId, managerId);

        mockMvc.perform(put("/api/v1/manage/teams/" + teamId)
                        .header("Authorization", "Bearer " + manager)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"BeforeRevoke\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        revoke(admin, teamId, managerId);

        mockMvc.perform(put("/api/v1/manage/teams/" + teamId)
                        .header("Authorization", "Bearer " + manager)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"AfterRevoke\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokingAdministratorTeamGrantDoesNotDemoteAdministrator() throws Exception {
        JsonNode adminLogin = TestAuthSupport.login(mockMvc, "admin-code");
        String admin = adminLogin.get("token").asText();
        long adminId = adminLogin.get("userId").asLong();
        long teamId = createTeam(admin, "AdminGrantTeam");

        grant(admin, teamId, adminId);
        revoke(admin, teamId, adminId);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    private long createTeam(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/teams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void grant(String token, long teamId, long userId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/teams/" + teamId + "/managers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + userId + "}"))
                .andExpect(status().isOk());
    }

    private void revoke(String token, long teamId, long userId) throws Exception {
        mockMvc.perform(delete("/api/v1/admin/teams/" + teamId + "/managers/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
