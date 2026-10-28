package com.campusfootball;

import com.campusfootball.competition.SuspensionService;
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

class SuspensionIntegrationTest extends IntegrationTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SuspensionService suspensionService;

    @Test
    void thirdAndSixthYellowCardsCreateTwoSuspensionsIdempotently() throws Exception {
        Setup s = setup();
        for (int minute = 1; minute <= 6; minute++) {
            TestApiSupport.addEvent(mockMvc, s.adminToken(), s.fixtureId(), s.homeTeamId(),
                    s.playerId(), "YELLOW_CARD", null, minute, 0);
        }
        TestApiSupport.finish(mockMvc, s.adminToken(), s.fixtureId(), 0, 0);
        suspensionService.recalculateSeason(s.seasonId());

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM player_suspension
                WHERE player_id = ? AND season_id = ? AND source_type = 'YELLOW_ACCUMULATION'
                """, Integer.class, s.playerId(), s.seasonId())).isEqualTo(2);
    }

    @Test
    void redCardCreatesOneSuspension() throws Exception {
        Setup s = setup();
        TestApiSupport.addEvent(mockMvc, s.adminToken(), s.fixtureId(), s.homeTeamId(),
                s.playerId(), "RED_CARD", null, 20, 0);
        TestApiSupport.finish(mockMvc, s.adminToken(), s.fixtureId(), 1, 0);

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM player_suspension
                WHERE player_id = ? AND source_type = 'RED_CARD' AND status = 'ACTIVE'
                """, Integer.class, s.playerId())).isEqualTo(1);
    }
    @Test
    void nextFinishedFixtureServesOneMatchWhenPlayerDidNotAppear() throws Exception {
        Setup s = setup();
        createRedCardSuspension(s);
        long nextFixture = createNextFixture(s);

        TestApiSupport.changeStatus(mockMvc, s.adminToken(), nextFixture, "SCHEDULED");
        TestApiSupport.changeStatus(mockMvc, s.adminToken(), nextFixture, "LIVE");
        TestApiSupport.finish(mockMvc, s.adminToken(), nextFixture, 1, 0);

        assertThat(jdbc.queryForObject("""
                SELECT matches_served FROM player_suspension
                WHERE player_id = ? AND source_type = 'RED_CARD'
                """, Integer.class, s.playerId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT status FROM player_suspension
                WHERE player_id = ? AND source_type = 'RED_CARD'
                """, String.class, s.playerId())).isEqualTo("SERVED");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM player_suspension_serving WHERE fixture_id = ?
                """, Integer.class, nextFixture)).isEqualTo(1);
    }

    @Test
    void appearanceInNextFixturePreventsSuspensionConsumption() throws Exception {
        Setup s = setup();
        createRedCardSuspension(s);
        long nextFixture = createNextFixture(s);
        TestApiSupport.changeStatus(mockMvc, s.adminToken(), nextFixture, "SCHEDULED");
        TestApiSupport.changeStatus(mockMvc, s.adminToken(), nextFixture, "LIVE");
        jdbc.update("""
                INSERT INTO fixture_appearance (fixture_id, team_id, player_id, is_starter, minutes_played, created_by)
                VALUES (?, ?, ?, 1, 90, ?)
                """, nextFixture, s.homeTeamId(), s.playerId(), s.adminUserId());
        TestApiSupport.finish(mockMvc, s.adminToken(), nextFixture, 1, 0);

        assertThat(jdbc.queryForObject("""
                SELECT matches_served FROM player_suspension
                WHERE player_id = ? AND source_type = 'RED_CARD'
                """, Integer.class, s.playerId())).isZero();
    }

    @Test
    void suspendedPlayerIsRejectedFromLineupAndEvents() throws Exception {
        Setup s = setup();
        createRedCardSuspension(s);
        long nextFixture = createNextFixture(s);
        TestApiSupport.changeStatus(mockMvc, s.adminToken(), nextFixture, "SCHEDULED");

        mockMvc.perform(put("/api/v1/manage/fixtures/" + nextFixture + "/teams/" + s.homeTeamId() + "/appearances")
                        .header("Authorization", "Bearer " + s.adminToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"starters\":[" + s.playerId() + "],\"substitutes\":[]}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/manage/fixtures/" + nextFixture + "/events")
                        .header("Authorization", "Bearer " + s.adminToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"teamId\":" + s.homeTeamId() + ",\"playerId\":" + s.playerId()
                                + ",\"eventType\":\"GOAL\",\"relatedPlayerId\":null,\"minute\":10,\"stoppageMinute\":0}"))
                .andExpect(status().isConflict());
    }
    @Test
    void manualSuspensionCanBeCreatedExtendedCancelledAndListed() throws Exception {
        Setup s = setup();
        long suspensionId = createManual(s, 2, "纪律处罚");

        mockMvc.perform(get("/api/v1/manage/teams/" + s.homeTeamId() + "/suspensions?seasonId=" + s.seasonId())
                        .header("Authorization", "Bearer " + s.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(suspensionId))
                .andExpect(jsonPath("$[0].matchesRemaining").value(2));

        mockMvc.perform(put("/api/v1/manage/suspensions/" + suspensionId)
                        .header("Authorization", "Bearer " + s.adminToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"matchesTotal\":3,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/manage/suspensions/" + suspensionId)
                        .header("Authorization", "Bearer " + s.adminToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"matchesTotal\":3,\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT matches_total FROM player_suspension WHERE id = ?",
                Integer.class, suspensionId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT status FROM player_suspension WHERE id = ?",
                String.class, suspensionId)).isEqualTo("CANCELLED");
    }

    @Test
    void leaderCanViewOwnTeamSuspensionsButCannotAdjustOrViewOtherTeam() throws Exception {
        Setup s = setup();
        createManual(s, 1, "领队查看测试");
        grantTeamRole(s, "TEAM_LEADER");
        JsonNode leaderLogin = TestAuthSupport.login(mockMvc, "leader-code");
        String leader = leaderLogin.get("token").asText();

        mockMvc.perform(get("/api/v1/manage/teams/" + s.homeTeamId() + "/suspensions?seasonId=" + s.seasonId())
                        .header("Authorization", "Bearer " + leader))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/manage/teams/" + s.awayTeamId() + "/suspensions?seasonId=" + s.seasonId())
                        .header("Authorization", "Bearer " + leader))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/manage/suspensions")
                        .header("Authorization", "Bearer " + leader)
                        .contentType(APPLICATION_JSON)
                        .content("{\"playerId\":" + s.playerId() + ",\"seasonId\":" + s.seasonId()
                                + ",\"matchesTotal\":1,\"reason\":\"越权\"}"))
                .andExpect(status().isForbidden());
    }
    @Test
    void deletingUnservedSourceEventRemovesAutomaticSuspension() throws Exception {
        Setup s = setup();
        long eventId = TestApiSupport.addEvent(mockMvc, s.adminToken(), s.fixtureId(), s.homeTeamId(),
                s.playerId(), "RED_CARD", null, 20, 0);
        TestApiSupport.finish(mockMvc, s.adminToken(), s.fixtureId(), 1, 0);
        TestApiSupport.setLocked(mockMvc, s.adminToken(), s.fixtureId(), false);

        mockMvc.perform(delete("/api/v1/manage/events/" + eventId)
                        .header("Authorization", "Bearer " + s.adminToken()))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM player_suspension WHERE source_event_id = ?",
                Integer.class, eventId)).isZero();
    }

    @Test
    void deletingServedSourceEventIsRejected() throws Exception {
        Setup s = setup();
        long eventId = TestApiSupport.addEvent(mockMvc, s.adminToken(), s.fixtureId(), s.homeTeamId(),
                s.playerId(), "RED_CARD", null, 20, 0);
        TestApiSupport.finish(mockMvc, s.adminToken(), s.fixtureId(), 1, 0);
        long nextFixture = createNextFixture(s);
        TestApiSupport.changeStatus(mockMvc, s.adminToken(), nextFixture, "SCHEDULED");
        TestApiSupport.changeStatus(mockMvc, s.adminToken(), nextFixture, "LIVE");
        TestApiSupport.finish(mockMvc, s.adminToken(), nextFixture, 1, 0);
        TestApiSupport.setLocked(mockMvc, s.adminToken(), s.fixtureId(), false);

        mockMvc.perform(delete("/api/v1/manage/events/" + eventId)
                        .header("Authorization", "Bearer " + s.adminToken()))
                .andExpect(status().isConflict());
    }
    private Setup setup() throws Exception {
        JsonNode adminLogin = TestAuthSupport.login(mockMvc, "admin-code");
        String admin = adminLogin.get("token").asText();
        long adminId = adminLogin.get("userId").asLong();
        long season = TestApiSupport.createSeason(mockMvc, admin, "SuspensionSeason", "ACTIVE");
        long home = TestApiSupport.createTeam(mockMvc, admin, "SuspensionHome");
        long away = TestApiSupport.createTeam(mockMvc, admin, "SuspensionAway");
        TestApiSupport.addSeasonTeam(mockMvc, admin, season, home);
        TestApiSupport.addSeasonTeam(mockMvc, admin, season, away);
        long fixture = TestApiSupport.createFixture(mockMvc, admin, season, home, away);
        TestApiSupport.changeStatus(mockMvc, admin, fixture, "SCHEDULED");
        TestApiSupport.changeStatus(mockMvc, admin, fixture, "LIVE");
        long player = TestApiSupport.createPlayer(mockMvc, admin, home, "SuspendedPlayer", 9);
        return new Setup(admin, adminId, season, home, away, fixture, player);
    }

    private long createNextFixture(Setup s) throws Exception {
        return TestApiSupport.createFixture(mockMvc, s.adminToken(), s.seasonId(), s.homeTeamId(), s.awayTeamId());
    }

    private void createRedCardSuspension(Setup s) throws Exception {
        TestApiSupport.addEvent(mockMvc, s.adminToken(), s.fixtureId(), s.homeTeamId(),
                s.playerId(), "RED_CARD", null, 20, 0);
        TestApiSupport.finish(mockMvc, s.adminToken(), s.fixtureId(), 1, 0);
    }

    private long createManual(Setup s, int matches, String reason) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/manage/suspensions")
                        .header("Authorization", "Bearer " + s.adminToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"playerId\":" + s.playerId() + ",\"seasonId\":" + s.seasonId()
                                + ",\"matchesTotal\":" + matches + ",\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void grantTeamRole(Setup s, String role) throws Exception {
        JsonNode leader = TestAuthSupport.login(mockMvc, "leader-code");
        mockMvc.perform(post("/api/v1/admin/teams/" + s.homeTeamId() + "/managers")
                        .header("Authorization", "Bearer " + s.adminToken())
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + leader.get("userId").asLong() + ",\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk());
    }

    private record Setup(String adminToken, long adminUserId, long seasonId, long homeTeamId,
                         long awayTeamId, long fixtureId, long playerId) {
    }
}
