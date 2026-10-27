package com.campusfootball;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppearanceIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void teamManagerAddsOwnTeamAppearanceInLiveFixture() throws Exception {
        Setup s = setup("live");
        long appearanceId = TestApiSupport.addAppearance(mockMvc, s.managerToken(), s.fixtureId(),
                s.homeTeamId(), s.homePlayerId(), true, 90);
        assertThat(appearanceId).isPositive();
    }

    @Test
    void teamManagerCannotAddOtherTeamAppearance() throws Exception {
        Setup s = setup("live");
        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/appearances")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(appearanceBody(s.awayTeamId(), s.awayPlayerId(), 90)))
                .andExpect(status().isForbidden());
    }

    @Test
    void playerFromDifferentTeamIsRejected() throws Exception {
        Setup s = setup("live");
        long otherTeam = TestApiSupport.createTeam(mockMvc, s.adminToken(), "OtherAppearanceTeam");
        long otherPlayer = TestApiSupport.createPlayer(mockMvc, s.adminToken(), otherTeam, "OtherPlayer", 21);

        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/appearances")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(appearanceBody(s.homeTeamId(), otherPlayer, 90)))
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateAppearanceReturns409() throws Exception {
        Setup s = setup("live");
        TestApiSupport.addAppearance(mockMvc, s.managerToken(), s.fixtureId(), s.homeTeamId(), s.homePlayerId(), true, 90);

        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/appearances")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(appearanceBody(s.homeTeamId(), s.homePlayerId(), 90)))
                .andExpect(status().isConflict());
    }

    @Test
    void teamManagerCannotWriteScheduledFixture() throws Exception {
        Setup s = setup("scheduled");
        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/appearances")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(appearanceBody(s.homeTeamId(), s.homePlayerId(), 90)))
                .andExpect(status().isForbidden());
    }

    @Test
    void teamManagerCanWriteFinishedUnlockedFixture() throws Exception {
        Setup s = setup("live");
        TestApiSupport.finish(mockMvc, s.adminToken(), s.fixtureId(), 1, 0);
        TestApiSupport.setLocked(mockMvc, s.adminToken(), s.fixtureId(), false);

        long appearanceId = TestApiSupport.addAppearance(mockMvc, s.managerToken(), s.fixtureId(),
                s.homeTeamId(), s.homePlayerId(), true, 90);
        assertThat(appearanceId).isPositive();
    }

    @Test
    void lockedFixtureRejectsAdminAndManagerWrites() throws Exception {
        Setup s = setup("live");
        TestApiSupport.finish(mockMvc, s.adminToken(), s.fixtureId(), 1, 0);

        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/appearances")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(appearanceBody(s.homeTeamId(), s.homePlayerId(), 90)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FIXTURE_LOCKED"));

        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/appearances")
                        .header("Authorization", "Bearer " + s.adminToken())
                        .contentType(APPLICATION_JSON)
                        .content(appearanceBody(s.awayTeamId(), s.awayPlayerId(), 90)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FIXTURE_LOCKED"));
    }

    @Test
    void managerCannotUpdateOrDeleteOtherTeamAppearance() throws Exception {
        Setup s = setup("live");
        long otherAppearance = TestApiSupport.addAppearance(mockMvc, s.adminToken(), s.fixtureId(),
                s.awayTeamId(), s.awayPlayerId(), true, 90);

        mockMvc.perform(put("/api/v1/manage/appearances/" + otherAppearance)
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"isStarter\":false,\"minutesPlayed\":45}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/manage/appearances/" + otherAppearance)
                        .header("Authorization", "Bearer " + s.managerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void appearanceOwnershipFieldsAreImmutable() throws Exception {
        Setup s = setup("live");
        long appearanceId = TestApiSupport.addAppearance(mockMvc, s.managerToken(), s.fixtureId(),
                s.homeTeamId(), s.homePlayerId(), true, 90);

        mockMvc.perform(put("/api/v1/manage/appearances/" + appearanceId)
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"isStarter\":false,\"minutesPlayed\":45,\"teamId\":" + s.awayTeamId() + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidMinutesReturns400() throws Exception {
        Setup s = setup("live");
        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/appearances")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(appearanceBody(s.homeTeamId(), s.homePlayerId(), 131)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void appearanceCreationCreatesAuditRow() throws Exception {
        Setup s = setup("live");
        TestApiSupport.addAppearance(mockMvc, s.managerToken(), s.fixtureId(), s.homeTeamId(), s.homePlayerId(), true, 90);
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE team_id = ? AND entity_type = 'APPEARANCE'",
                Integer.class, s.homeTeamId());
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void appearanceFreezesFixtureParticipants() throws Exception {
        Setup s = setup("live");
        long replacementTeam = TestApiSupport.createTeam(mockMvc, s.adminToken(), "ReplacementTeam");
        TestApiSupport.addSeasonTeam(mockMvc, s.adminToken(), s.seasonId(), replacementTeam);
        TestApiSupport.addAppearance(mockMvc, s.managerToken(), s.fixtureId(), s.homeTeamId(), s.homePlayerId(), true, 90);

        mockMvc.perform(put("/api/v1/admin/fixtures/" + s.fixtureId())
                        .header("Authorization", "Bearer " + s.adminToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"seasonId\":" + s.seasonId() + ",\"homeTeamId\":" + s.homeTeamId()
                                + ",\"awayTeamId\":" + replacementTeam + "}"))
                .andExpect(status().isConflict());
    }

    private Setup setup(String status) throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long seasonId = TestApiSupport.createSeason(mockMvc, admin, "AppearanceSeason", "ACTIVE");
        long home = TestApiSupport.createTeam(mockMvc, admin, "AppearanceHome");
        long away = TestApiSupport.createTeam(mockMvc, admin, "AppearanceAway");
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, home);
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, away);
        long fixtureId = TestApiSupport.createFixture(mockMvc, admin, seasonId, home, away);
        if ("scheduled".equals(status)) {
            TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "SCHEDULED");
        } else if ("live".equals(status)) {
            TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "SCHEDULED");
            TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "LIVE");
        }
        long homePlayer = TestApiSupport.createPlayer(mockMvc, admin, home, "HomePlayer", 9);
        long awayPlayer = TestApiSupport.createPlayer(mockMvc, admin, away, "AwayPlayer", 10);
        JsonNode managerLogin = TestAuthSupport.login(mockMvc, "manager-code");
        mockMvc.perform(post("/api/v1/admin/teams/" + home + "/managers")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + managerLogin.get("userId").asLong() + "}"))
                .andExpect(status().isOk());
        return new Setup(admin, managerLogin.get("token").asText(), seasonId, home, away,
                fixtureId, homePlayer, awayPlayer);
    }

    private String appearanceBody(long teamId, long playerId, int minutes) {
        return "{\"teamId\":" + teamId + ",\"playerId\":" + playerId
                + ",\"isStarter\":true,\"minutesPlayed\":" + minutes + "}";
    }

    private record Setup(String adminToken, String managerToken, long seasonId, long homeTeamId,
                         long awayTeamId, long fixtureId, long homePlayerId, long awayPlayerId) {
    }
}
