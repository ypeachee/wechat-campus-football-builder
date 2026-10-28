package com.campusfootball;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamLeaderIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void leaderCanMaintainOwnTeamAndScheduledLineup() throws Exception {
        Setup s = setup("SCHEDULED");

        mockMvc.perform(put("/api/v1/manage/teams/" + s.homeTeamId())
                        .header("Authorization", "Bearer " + s.leaderToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"LeaderEditedHome\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/manage/teams/" + s.homeTeamId() + "/players")
                        .header("Authorization", "Bearer " + s.leaderToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"LeaderPlayer\",\"jerseyNo\":17}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/manage/fixtures/" + s.fixtureId() + "/teams/" + s.homeTeamId() + "/appearances")
                        .header("Authorization", "Bearer " + s.leaderToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"starters\":[" + s.homePlayerId() + "],\"substitutes\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    void leaderCannotChangeTeamStatusOrManageOtherTeam() throws Exception {
        Setup s = setup("SCHEDULED");

        mockMvc.perform(put("/api/v1/manage/teams/" + s.homeTeamId())
                        .header("Authorization", "Bearer " + s.leaderToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"LeaderCannotDisable\",\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/manage/teams/" + s.awayTeamId() + "/players")
                        .header("Authorization", "Bearer " + s.leaderToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"OtherTeamPlayer\",\"jerseyNo\":18}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/manage/fixtures/" + s.fixtureId() + "/teams/" + s.awayTeamId() + "/appearances")
                        .header("Authorization", "Bearer " + s.leaderToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"starters\":[" + s.awayPlayerId() + "],\"substitutes\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void leaderCannotWriteEventsOrUseGlobalAdminApis() throws Exception {
        Setup s = setup("LIVE");

        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/events")
                        .header("Authorization", "Bearer " + s.leaderToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"teamId\":" + s.homeTeamId() + ",\"playerId\":" + s.homePlayerId()
                                + ",\"eventType\":\"GOAL\",\"relatedPlayerId\":null,\"minute\":10,\"stoppageMinute\":0}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/teams")
                        .header("Authorization", "Bearer " + s.leaderToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/competitions")
                        .header("Authorization", "Bearer " + s.leaderToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void lockedFixtureRejectsLeaderLineup() throws Exception {
        Setup s = setup("FINISHED_LOCKED");

        mockMvc.perform(put("/api/v1/manage/fixtures/" + s.fixtureId() + "/teams/" + s.homeTeamId() + "/appearances")
                        .header("Authorization", "Bearer " + s.leaderToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"starters\":[" + s.homePlayerId() + "],\"substitutes\":[]}"))
                .andExpect(status().isForbidden());
    }

    private Setup setup(String status) throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long seasonId = TestApiSupport.createSeason(mockMvc, admin, "LeaderSeason", "ACTIVE");
        long home = TestApiSupport.createTeam(mockMvc, admin, "LeaderHome");
        long away = TestApiSupport.createTeam(mockMvc, admin, "LeaderAway");
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, home);
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, away);
        long fixtureId = TestApiSupport.createFixture(mockMvc, admin, seasonId, home, away);
        long homePlayer = TestApiSupport.createPlayer(mockMvc, admin, home, "LeaderHomePlayer", 9);
        long awayPlayer = TestApiSupport.createPlayer(mockMvc, admin, away, "LeaderAwayPlayer", 10);
        if ("SCHEDULED".equals(status)) {
            TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "SCHEDULED");
        } else if ("LIVE".equals(status)) {
            TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "SCHEDULED");
            TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "LIVE");
        } else if ("FINISHED_LOCKED".equals(status)) {
            TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "SCHEDULED");
            TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "LIVE");
            TestApiSupport.finish(mockMvc, admin, fixtureId, 1, 0);
        }
        JsonNode leaderLogin = TestAuthSupport.login(mockMvc, "leader-code");
        mockMvc.perform(post("/api/v1/admin/teams/" + home + "/managers")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + leaderLogin.get("userId").asLong() + ",\"role\":\"TEAM_LEADER\"}"))
                .andExpect(status().isOk());
        return new Setup(home, away, fixtureId, homePlayer, awayPlayer, leaderLogin.get("token").asText());
    }

    private record Setup(long homeTeamId, long awayTeamId, long fixtureId, long homePlayerId,
                         long awayPlayerId, String leaderToken) {
    }
}
