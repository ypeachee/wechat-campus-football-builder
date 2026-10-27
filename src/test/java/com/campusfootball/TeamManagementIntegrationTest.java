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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamManagementIntegrationTest extends IntegrationTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCreatesTeamAndPlayerVisiblePublicly() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = createTeam(admin, "Alpha");

        long playerId = createPlayer(admin, teamId, "Striker", 9);
        assertThat(playerId).isPositive();

        mockMvc.perform(get("/api/v1/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alpha"));
        mockMvc.perform(get("/api/v1/teams/" + teamId + "/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jerseyNo").value(9));
        mockMvc.perform(get("/api/v1/players/" + playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Striker"));
    }

    @Test
    void teamManagerCanEditOwnTeamButCannotChangeStatus() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = createTeam(admin, "OwnTeam");
        JsonNode managerLogin = TestAuthSupport.login(mockMvc, "manager-code");
        long managerId = managerLogin.get("userId").asLong();
        String manager = managerLogin.get("token").asText();
        grant(admin, teamId, managerId);

        mockMvc.perform(put("/api/v1/manage/teams/" + teamId)
                        .header("Authorization", "Bearer " + manager)
                        .contentType(APPLICATION_JSON)
                        .content(teamBody("OwnTeam-Renamed", "ACTIVE")))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/manage/teams/" + teamId)
                        .header("Authorization", "Bearer " + manager)
                        .contentType(APPLICATION_JSON)
                        .content(teamBody("OwnTeam-Renamed", "INACTIVE")))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT name FROM team WHERE id = ?", String.class, teamId))
                .isEqualTo("OwnTeam-Renamed");
        assertThat(jdbc.queryForObject("SELECT status FROM team WHERE id = ?", String.class, teamId))
                .isEqualTo("ACTIVE");
    }

    @Test
    void teamManagerCannotEditOtherTeamOrPlayer() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long ownTeam = createTeam(admin, "Mine");
        long otherTeam = createTeam(admin, "Other");
        long otherPlayer = createPlayer(admin, otherTeam, "OtherPlayer", 7);
        JsonNode managerLogin = TestAuthSupport.login(mockMvc, "manager-code");
        String manager = managerLogin.get("token").asText();
        grant(admin, ownTeam, managerLogin.get("userId").asLong());

        mockMvc.perform(put("/api/v1/manage/teams/" + otherTeam)
                        .header("Authorization", "Bearer " + manager)
                        .contentType(APPLICATION_JSON)
                        .content(teamBody("Hacked", "ACTIVE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/manage/players/" + otherPlayer)
                        .header("Authorization", "Bearer " + manager)
                        .contentType(APPLICATION_JSON)
                        .content(playerBody("HackedPlayer", 7, "ACTIVE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerCannotCallManageEndpoints() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = createTeam(admin, "ViewerTeam");
        String viewer = TestAuthSupport.loginToken(mockMvc, "viewer-code");

        mockMvc.perform(put("/api/v1/manage/teams/" + teamId)
                        .header("Authorization", "Bearer " + viewer)
                        .contentType(APPLICATION_JSON)
                        .content(teamBody("Nope", "ACTIVE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateActiveJerseyReturns409() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = createTeam(admin, "JerseyTeam");
        createPlayer(admin, teamId, "First", 10);

        mockMvc.perform(post("/api/v1/manage/teams/" + teamId + "/players")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Second\",\"jerseyNo\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void inactiveTeamAndPlayerAreHiddenFromPublicReads() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = createTeam(admin, "HiddenTeam");
        long playerId = createPlayer(admin, teamId, "HiddenPlayer", 8);

        mockMvc.perform(put("/api/v1/admin/teams/" + teamId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content(teamBody("HiddenTeam", "INACTIVE")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/teams/" + teamId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/teams/" + teamId + "/players")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/players/" + playerId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/teams").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("INACTIVE"));
        mockMvc.perform(get("/api/v1/admin/teams/" + teamId + "/players")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(playerId));
    }

    @Test
    void writesCreateTeamScopedAuditRows() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = createTeam(admin, "AuditTeam");
        createPlayer(admin, teamId, "AuditPlayer", 11);

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE team_id = ? AND entity_type IN ('TEAM','PLAYER')",
                Integer.class, teamId);
        assertThat(rows).isEqualTo(2);
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

    private long createPlayer(String token, long teamId, String name, int jerseyNo) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/manage/teams/" + teamId + "/players")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"jerseyNo\":" + jerseyNo + "}"))
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

    private String teamBody(String name, String status) {
        return "{\"name\":\"" + name + "\",\"status\":\"" + status + "\"}";
    }

    private String playerBody(String name, int jerseyNo, String status) {
        return "{\"name\":\"" + name + "\",\"jerseyNo\":" + jerseyNo + ",\"status\":\"" + status + "\"}";
    }
}

