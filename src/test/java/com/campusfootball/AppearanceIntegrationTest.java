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
    void replaceLineupSavesStartersAndSubstitutesInOneRequest() throws Exception {
        Setup s = setup("live");

        mockMvc.perform(put("/api/v1/manage/fixtures/" + s.fixtureId() + "/teams/" + s.homeTeamId() + "/appearances")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(lineupBody(s.homePlayerId() + "," + s.homePlayer2Id(),
                                substituteBody(s.homePlayer3Id(), 32))))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM fixture_appearance WHERE fixture_id = ? AND team_id = ?",
                Integer.class, s.fixtureId(), s.homeTeamId())).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT is_starter FROM fixture_appearance WHERE fixture_id = ? AND player_id = ?",
                Boolean.class, s.fixtureId(), s.homePlayerId())).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT minutes_played FROM fixture_appearance WHERE fixture_id = ? AND player_id = ?",
                Integer.class, s.fixtureId(), s.homePlayerId())).isEqualTo(90);
        assertThat(jdbc.queryForObject(
                "SELECT is_starter FROM fixture_appearance WHERE fixture_id = ? AND player_id = ?",
                Boolean.class, s.fixtureId(), s.homePlayer3Id())).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT minutes_played FROM fixture_appearance WHERE fixture_id = ? AND player_id = ?",
                Integer.class, s.fixtureId(), s.homePlayer3Id())).isEqualTo(32);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE team_id = ? AND entity_type = 'TEAM_LINEUP'",
                Integer.class, s.homeTeamId())).isEqualTo(1);
    }

    @Test
    void replaceLineupRejectsPlayerSelectedAsBothStarterAndSubstitute() throws Exception {
        Setup s = setup("live");

        mockMvc.perform(put("/api/v1/manage/fixtures/" + s.fixtureId() + "/teams/" + s.homeTeamId() + "/appearances")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(lineupBody(Long.toString(s.homePlayerId()), substituteBody(s.homePlayerId(), 30))))
                .andExpect(status().isConflict());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM fixture_appearance WHERE fixture_id = ? AND team_id = ?",
                Integer.class, s.fixtureId(), s.homeTeamId())).isZero();
    }

    @Test
    void replaceLineupRejectsPlayerFromAnotherTeam() throws Exception {
        Setup s = setup("live");
        long otherTeam = TestApiSupport.createTeam(mockMvc, s.adminToken(), "OtherLineupTeam");
        long otherPlayer = TestApiSupport.createPlayer(mockMvc, s.adminToken(), otherTeam, "OtherLineupPlayer", 21);

        mockMvc.perform(put("/api/v1/manage/fixtures/" + s.fixtureId() + "/teams/" + s.homeTeamId() + "/appearances")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(lineupBody(Long.toString(otherPlayer), "")))
                .andExpect(status().isConflict());
    }

    @Test
    void replaceLineupCannotDropPlayerWhoAlreadyHasAnEvent() throws Exception {
        Setup s = setup("live");
        TestApiSupport.addEvent(mockMvc, s.managerToken(), s.fixtureId(), s.homeTeamId(),
                s.homePlayer2Id(), "GOAL", null, 12, 0);

        mockMvc.perform(put("/api/v1/manage/fixtures/" + s.fixtureId() + "/teams/" + s.homeTeamId() + "/appearances")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(lineupBody(Long.toString(s.homePlayerId()), "")))
                .andExpect(status().isConflict());
    }

    @Test
    void replaceLineupReplacesPreviousTeamLineup() throws Exception {
        Setup s = setup("live");
        String path = "/api/v1/manage/fixtures/" + s.fixtureId() + "/teams/" + s.homeTeamId() + "/appearances";

        mockMvc.perform(put(path)
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(lineupBody(Long.toString(s.homePlayerId()), substituteBody(s.homePlayer2Id(), 20))))
                .andExpect(status().isOk());
        mockMvc.perform(put(path)
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(lineupBody(Long.toString(s.homePlayer2Id()), substituteBody(s.homePlayerId(), 45))))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM fixture_appearance WHERE fixture_id = ? AND team_id = ?",
                Integer.class, s.fixtureId(), s.homeTeamId())).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT is_starter FROM fixture_appearance WHERE fixture_id = ? AND player_id = ?",
                Boolean.class, s.fixtureId(), s.homePlayer2Id())).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT is_starter FROM fixture_appearance WHERE fixture_id = ? AND player_id = ?",
                Boolean.class, s.fixtureId(), s.homePlayerId())).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT minutes_played FROM fixture_appearance WHERE fixture_id = ? AND player_id = ?",
                Integer.class, s.fixtureId(), s.homePlayerId())).isEqualTo(45);
    }

    @Test
    void replaceLineupRejectsUnauthorizedTeamManager() throws Exception {
        Setup s = setup("live");

        mockMvc.perform(put("/api/v1/manage/fixtures/" + s.fixtureId() + "/teams/" + s.awayTeamId() + "/appearances")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(lineupBody(Long.toString(s.awayPlayerId()), "")))
                .andExpect(status().isForbidden());
    }

    @Test
    void replaceLineupRejectsLockedFixture() throws Exception {
        Setup s = setup("live");
        TestApiSupport.finish(mockMvc, s.adminToken(), s.fixtureId(), 1, 0);

        mockMvc.perform(put("/api/v1/manage/fixtures/" + s.fixtureId() + "/teams/" + s.homeTeamId() + "/appearances")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(lineupBody(Long.toString(s.homePlayerId()), "")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FIXTURE_LOCKED"));
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
        long homePlayer2 = TestApiSupport.createPlayer(mockMvc, admin, home, "HomePlayer2", 11);
        long homePlayer3 = TestApiSupport.createPlayer(mockMvc, admin, home, "HomePlayer3", 7);
        long awayPlayer = TestApiSupport.createPlayer(mockMvc, admin, away, "AwayPlayer", 10);
        JsonNode managerLogin = TestAuthSupport.login(mockMvc, "manager-code");
        mockMvc.perform(post("/api/v1/admin/teams/" + home + "/managers")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + managerLogin.get("userId").asLong() + "}"))
                .andExpect(status().isOk());
        return new Setup(admin, managerLogin.get("token").asText(), seasonId, home, away,
                fixtureId, homePlayer, homePlayer2, homePlayer3, awayPlayer);
    }

    private String appearanceBody(long teamId, long playerId, int minutes) {
        return "{\"teamId\":" + teamId + ",\"playerId\":" + playerId
                + ",\"isStarter\":true,\"minutesPlayed\":" + minutes + "}";
    }

    private String lineupBody(String starters, String substitutes) {
        return "{\"starters\":[" + starters + "],\"substitutes\":[" + substitutes + "]}";
    }

    private String substituteBody(long playerId, int minutes) {
        return "{\"playerId\":" + playerId + ",\"minutesPlayed\":" + minutes + "}";
    }

    private record Setup(String adminToken, String managerToken, long seasonId, long homeTeamId,
                         long awayTeamId, long fixtureId, long homePlayerId, long homePlayer2Id,
                         long homePlayer3Id, long awayPlayerId) {
    }
}
