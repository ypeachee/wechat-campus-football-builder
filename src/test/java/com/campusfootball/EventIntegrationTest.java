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

class EventIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void recordsGoalWithAssistAndGoalWithoutAssist() throws Exception {
        Setup s = setup("live");
        long assisted = TestApiSupport.addEvent(mockMvc, s.managerToken(), s.fixtureId(), s.homeTeamId(),
                s.homePlayerId(), "GOAL", s.secondHomePlayerId(), 10, 0);
        long unassisted = TestApiSupport.addEvent(mockMvc, s.managerToken(), s.fixtureId(), s.homeTeamId(),
                s.homePlayerId(), "GOAL", null, 20, 0);
        assertThat(assisted).isPositive();
        assertThat(unassisted).isPositive();
    }

    @Test
    void rejectsCrossTeamAssistAndSelfAssist() throws Exception {
        Setup s = setup("live");
        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/events")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(eventBody(s.homeTeamId(), s.homePlayerId(), "GOAL", s.awayPlayerId(), 10, 0)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/events")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(eventBody(s.homeTeamId(), s.homePlayerId(), "GOAL", s.homePlayerId(), 11, 0)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsRelatedPlayerForCardsAndOwnGoals() throws Exception {
        Setup s = setup("live");
        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/events")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(eventBody(s.homeTeamId(), s.homePlayerId(), "YELLOW_CARD", s.secondHomePlayerId(), 15, 0)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/events")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(eventBody(s.homeTeamId(), s.awayPlayerId(), "OWN_GOAL", s.homePlayerId(), 16, 0)))
                .andExpect(status().isConflict());
    }

    @Test
    void ownGoalUsesBeneficiaryTeamAndOpponentPlayer() throws Exception {
        Setup s = setup("live");
        long eventId = TestApiSupport.addEvent(mockMvc, s.managerToken(), s.fixtureId(), s.homeTeamId(),
                s.awayPlayerId(), "OWN_GOAL", null, 30, 0);

        assertThat(jdbc.queryForObject("SELECT team_id FROM fixture_event WHERE id = ?", Long.class, eventId))
                .isEqualTo(s.homeTeamId());
        assertThat(jdbc.queryForObject("SELECT player_id FROM fixture_event WHERE id = ?", Long.class, eventId))
                .isEqualTo(s.awayPlayerId());
    }

    @Test
    void playerOutsideFixtureIsRejected() throws Exception {
        Setup s = setup("live");
        long thirdTeam = TestApiSupport.createTeam(mockMvc, s.adminToken(), "ThirdEventTeam");
        long thirdPlayer = TestApiSupport.createPlayer(mockMvc, s.adminToken(), thirdTeam, "ThirdPlayer", 33);

        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/events")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(eventBody(s.homeTeamId(), thirdPlayer, "GOAL", null, 22, 0)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidMinuteOrStoppageReturns400() throws Exception {
        Setup s = setup("live");
        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/events")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(eventBody(s.homeTeamId(), s.homePlayerId(), "GOAL", null, 0, 0)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/events")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(eventBody(s.homeTeamId(), s.homePlayerId(), "GOAL", null, 10, 31)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void teamManagerCannotCreateOrEditOtherTeamEvent() throws Exception {
        Setup s = setup("live");
        long awayEvent = TestApiSupport.addEvent(mockMvc, s.adminToken(), s.fixtureId(), s.awayTeamId(),
                s.awayPlayerId(), "GOAL", null, 40, 0);

        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/events")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(eventBody(s.awayTeamId(), s.awayPlayerId(), "YELLOW_CARD", null, 41, 0)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/manage/events/" + awayEvent)
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(updateEventBody(s.awayPlayerId(), "GOAL", null, 42, 0)))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCannotWriteScheduledButAdminCan() throws Exception {
        Setup s = setup("scheduled");
        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/events")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(eventBody(s.homeTeamId(), s.homePlayerId(), "GOAL", null, 10, 0)))
                .andExpect(status().isForbidden());

        long id = TestApiSupport.addEvent(mockMvc, s.adminToken(), s.fixtureId(), s.homeTeamId(),
                s.homePlayerId(), "GOAL", null, 10, 0);
        assertThat(id).isPositive();
    }

    @Test
    void lockedFixtureRejectsAdminAndManagerEventWrites() throws Exception {
        Setup s = setup("live");
        TestApiSupport.finish(mockMvc, s.adminToken(), s.fixtureId(), 1, 0);

        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/events")
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(eventBody(s.homeTeamId(), s.homePlayerId(), "GOAL", null, 10, 0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FIXTURE_LOCKED"));

        mockMvc.perform(post("/api/v1/manage/fixtures/" + s.fixtureId() + "/events")
                        .header("Authorization", "Bearer " + s.adminToken())
                        .contentType(APPLICATION_JSON)
                        .content(eventBody(s.homeTeamId(), s.homePlayerId(), "GOAL", null, 10, 0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FIXTURE_LOCKED"));
    }

    @Test
    void eventTeamIsImmutableAndManagerCanUpdateAndDeleteOwnEvent() throws Exception {
        Setup s = setup("live");
        long eventId = TestApiSupport.addEvent(mockMvc, s.managerToken(), s.fixtureId(), s.homeTeamId(),
                s.homePlayerId(), "GOAL", null, 10, 0);

        mockMvc.perform(put("/api/v1/manage/events/" + eventId)
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"teamId\":" + s.awayTeamId() + ",\"playerId\":" + s.homePlayerId()
                                + ",\"eventType\":\"GOAL\",\"minute\":20,\"stoppageMinute\":0}"))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/manage/events/" + eventId)
                        .header("Authorization", "Bearer " + s.managerToken())
                        .contentType(APPLICATION_JSON)
                        .content(updateEventBody(s.homePlayerId(), "GOAL", null, 20, 0)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/manage/events/" + eventId)
                        .header("Authorization", "Bearer " + s.managerToken()))
                .andExpect(status().isOk());
    }

    @Test
    void goalDoesNotChangeOfficialScore() throws Exception {
        Setup s = setup("live");
        TestApiSupport.addEvent(mockMvc, s.managerToken(), s.fixtureId(), s.homeTeamId(),
                s.homePlayerId(), "GOAL", null, 10, 0);

        assertThat(jdbc.queryForObject("SELECT home_score FROM fixture WHERE id = ?", Integer.class, s.fixtureId()))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT away_score FROM fixture WHERE id = ?", Integer.class, s.fixtureId()))
                .isNull();
    }

    @Test
    void eventCreationCreatesAuditRow() throws Exception {
        Setup s = setup("live");
        TestApiSupport.addEvent(mockMvc, s.managerToken(), s.fixtureId(), s.homeTeamId(),
                s.homePlayerId(), "GOAL", null, 10, 0);
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE team_id = ? AND entity_type = 'EVENT'",
                Integer.class, s.homeTeamId());
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void eventFreezesFixtureParticipants() throws Exception {
        Setup s = setup("live");
        long replacementTeam = TestApiSupport.createTeam(mockMvc, s.adminToken(), "ReplacementEventTeam");
        TestApiSupport.addSeasonTeam(mockMvc, s.adminToken(), s.seasonId(), replacementTeam);
        TestApiSupport.addEvent(mockMvc, s.managerToken(), s.fixtureId(), s.homeTeamId(),
                s.homePlayerId(), "GOAL", null, 10, 0);

        mockMvc.perform(put("/api/v1/admin/fixtures/" + s.fixtureId())
                        .header("Authorization", "Bearer " + s.adminToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"seasonId\":" + s.seasonId() + ",\"homeTeamId\":" + s.homeTeamId()
                                + ",\"awayTeamId\":" + replacementTeam + "}"))
                .andExpect(status().isConflict());
    }

    private Setup setup(String status) throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long seasonId = TestApiSupport.createSeason(mockMvc, admin, "EventSeason", "ACTIVE");
        long home = TestApiSupport.createTeam(mockMvc, admin, "EventHome");
        long away = TestApiSupport.createTeam(mockMvc, admin, "EventAway");
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, home);
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, away);
        long fixtureId = TestApiSupport.createFixture(mockMvc, admin, seasonId, home, away);
        if ("scheduled".equals(status)) {
            TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "SCHEDULED");
        } else if ("live".equals(status)) {
            TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "SCHEDULED");
            TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "LIVE");
        }
        long homePlayer = TestApiSupport.createPlayer(mockMvc, admin, home, "HomeScorer", 9);
        long secondHomePlayer = TestApiSupport.createPlayer(mockMvc, admin, home, "HomeAssister", 8);
        long awayPlayer = TestApiSupport.createPlayer(mockMvc, admin, away, "AwayPlayer", 10);
        JsonNode managerLogin = TestAuthSupport.login(mockMvc, "manager-code");
        mockMvc.perform(post("/api/v1/admin/teams/" + home + "/managers")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + managerLogin.get("userId").asLong() + "}"))
                .andExpect(status().isOk());
        return new Setup(admin, managerLogin.get("token").asText(), seasonId, home, away,
                fixtureId, homePlayer, secondHomePlayer, awayPlayer);
    }

    private String eventBody(long teamId, long playerId, String type, Long relatedPlayerId,
                             int minute, int stoppageMinute) {
        String related = relatedPlayerId == null ? "null" : relatedPlayerId.toString();
        return "{\"teamId\":" + teamId + ",\"playerId\":" + playerId + ",\"eventType\":\"" + type
                + "\",\"relatedPlayerId\":" + related + ",\"minute\":" + minute
                + ",\"stoppageMinute\":" + stoppageMinute + "}";
    }

    private String updateEventBody(long playerId, String type, Long relatedPlayerId, int minute, int stoppageMinute) {
        String related = relatedPlayerId == null ? "null" : relatedPlayerId.toString();
        return "{\"playerId\":" + playerId + ",\"eventType\":\"" + type
                + "\",\"relatedPlayerId\":" + related + ",\"minute\":" + minute
                + ",\"stoppageMinute\":" + stoppageMinute + "}";
    }

    private record Setup(String adminToken, String managerToken, long seasonId, long homeTeamId,
                         long awayTeamId, long fixtureId, long homePlayerId, long secondHomePlayerId,
                         long awayPlayerId) {
    }
}
