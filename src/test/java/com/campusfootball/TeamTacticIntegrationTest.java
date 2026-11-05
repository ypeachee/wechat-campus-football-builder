package com.campusfootball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamTacticIntegrationTest extends IntegrationTestBase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCanCreateUpdateListAndDeleteTactic() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = TestApiSupport.createTeam(mockMvc, admin, "TacticAdminTeam");
        long p1 = TestApiSupport.createPlayer(mockMvc, admin, teamId, "Tactic Player 1", 9);
        long p2 = TestApiSupport.createPlayer(mockMvc, admin, teamId, "Tactic Player 2", 10);

        long tacticId = createTactic(admin, teamId, "常规首发",
                "[{\"playerId\":" + p1 + ",\"x\":30.5,\"y\":78},{\"playerId\":" + p2 + ",\"x\":70,\"y\":42.5}]");

        mockMvc.perform(get("/api/v1/manage/teams/" + teamId + "/tactics")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("常规首发"))
                .andExpect(jsonPath("$[0].positions[0].x").value(30.5));

        mockMvc.perform(put("/api/v1/manage/tactics/" + tacticId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"防守方案\",\"positions\":[{\"playerId\":" + p1
                                + ",\"x\":12.5,\"y\":64}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("防守方案"))
                .andExpect(jsonPath("$.positions.length()").value(1));

        mockMvc.perform(delete("/api/v1/manage/tactics/" + tacticId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/manage/teams/" + teamId + "/tactics")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void leaderCanManageOwnTeamTacticOnly() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long homeTeam = TestApiSupport.createTeam(mockMvc, admin, "TacticLeaderHome");
        long otherTeam = TestApiSupport.createTeam(mockMvc, admin, "TacticLeaderOther");
        long player = TestApiSupport.createPlayer(mockMvc, admin, homeTeam, "Leader Tactic Player", 7);
        JsonNode leader = TestAuthSupport.login(mockMvc, "leader-code");
        String leaderToken = leader.get("token").asText();
        grant(admin, homeTeam, leader.get("userId").asLong(), "TEAM_LEADER");

        mockMvc.perform(post("/api/v1/manage/teams/" + homeTeam + "/tactics")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"领队方案\",\"positions\":[{\"playerId\":" + player
                                + ",\"x\":50,\"y\":50}]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/manage/teams/" + otherTeam + "/tactics")
                        .header("Authorization", "Bearer " + leaderToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"越权方案\",\"positions\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsOtherTeamPlayerAndDuplicateTacticName() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = TestApiSupport.createTeam(mockMvc, admin, "TacticValidationTeam");
        long otherTeam = TestApiSupport.createTeam(mockMvc, admin, "TacticValidationOther");
        long teamPlayer = TestApiSupport.createPlayer(mockMvc, admin, teamId, "Valid Tactic Player", 4);
        long otherPlayer = TestApiSupport.createPlayer(mockMvc, admin, otherTeam, "Invalid Tactic Player", 5);

        mockMvc.perform(post("/api/v1/manage/teams/" + teamId + "/tactics")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"重复名称\",\"positions\":[]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/manage/teams/" + teamId + "/tactics")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"重复名称\",\"positions\":[]}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/manage/teams/" + teamId + "/tactics")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"跨队方案\",\"positions\":[{\"playerId\":" + otherPlayer
                                + ",\"x\":10,\"y\":10}]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/manage/teams/" + teamId + "/tactics")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"越界坐标\",\"positions\":[{\"playerId\":" + teamPlayer
                                + ",\"x\":120,\"y\":10}]}"))
                .andExpect(status().isBadRequest());
    }

    private long createTactic(String token, long teamId, String name, String positionsJson) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/manage/teams/" + teamId + "/tactics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"positions\":" + positionsJson + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void grant(String adminToken, long teamId, long userId, String role) throws Exception {
        mockMvc.perform(post("/api/v1/admin/teams/" + teamId + "/managers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + userId + ",\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk());
    }
}
