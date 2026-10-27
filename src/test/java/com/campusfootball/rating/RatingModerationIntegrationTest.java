package com.campusfootball.rating;

import com.campusfootball.IntegrationTestBase;
import com.campusfootball.TestAuthSupport;
import com.campusfootball.competition.FixtureStatus;
import com.campusfootball.competition.SeasonStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RatingModerationIntegrationTest extends IntegrationTestBase {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminApprovesThenHidesRatingAndWritesAudit() throws Exception {
        RatingFixture fixture = fixture();
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        String viewer = TestAuthSupport.loginToken(mockMvc, "viewer-code");
        long ratingId = insertRating(fixture, data.findUserId("openid-viewer"), 4, "待审核评价", "PENDING");

        mockMvc.perform(get("/api/v1/admin/ratings?status=PENDING")
                        .header("Authorization", "Bearer " + viewer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/ratings?status=PENDING")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(ratingId));

        mockMvc.perform(put("/api/v1/admin/ratings/" + ratingId + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"VISIBLE\",\"reason\":\"审核通过\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/fixtures/" + fixture.fixtureId() + "/player-ratings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].averageScore").value(8.0))
                .andExpect(jsonPath("$[0].ratingCount").value(1));

        mockMvc.perform(put("/api/v1/admin/ratings/" + ratingId + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"HIDDEN\",\"reason\":\"下线\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/fixtures/" + fixture.fixtureId() + "/player-ratings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ratingCount").value(0));

        Integer auditCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_log WHERE action = 'MODERATE' AND entity_type = 'PLAYER_RATING'
                """, Integer.class);
        assertThat(auditCount).isEqualTo(2);
    }

    @Test
    void resolvesOpenReportsWhenModerating() throws Exception {
        RatingFixture fixture = fixture();
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        String viewer = TestAuthSupport.loginToken(mockMvc, "viewer-code");
        long ratingId = insertRating(fixture, data.findUserId("openid-admin"), 5, "被举报评价", "VISIBLE");
        jdbc.update("INSERT INTO rating_report (rating_id, reporter_user_id, reason) VALUES (?, ?, '不当内容')",
                ratingId, data.findUserId("openid-viewer"));

        mockMvc.perform(get("/api/v1/admin/ratings?status=REPORTED")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].reporterCount").value(1));

        mockMvc.perform(put("/api/v1/admin/ratings/" + ratingId + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"HIDDEN\",\"reason\":\"举报成立\"}"))
                .andExpect(status().isOk());

        Integer openReports = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rating_report WHERE rating_id = ? AND status = 'OPEN'",
                Integer.class, ratingId);
        assertThat(openReports).isZero();
    }

    private long insertRating(RatingFixture fixture, long userId, int stars, String comment, String status) {
        jdbc.update("""
                INSERT INTO player_match_rating
                    (fixture_id, player_id, team_id, user_id, stars, comment, status, edits_used)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """, fixture.fixtureId(), fixture.playerId(), fixture.teamId(), userId, stars, comment, status);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private RatingFixture fixture() {
        long adminId = data.findUserId("openid-admin");
        long seasonId = data.createSeason("RatingModeration", SeasonStatus.ACTIVE);
        long home = data.createTeam("ModerationHome");
        long away = data.createTeam("ModerationAway");
        data.addSeasonTeam(seasonId, home);
        data.addSeasonTeam(seasonId, away);
        long fixtureId = data.createFixture(seasonId, home, away, FixtureStatus.FINISHED,
                adminId, 1, 0, true);
        long playerId = data.createPlayer(home, "ModerationPlayer", 8);
        data.createAppearance(fixtureId, home, playerId, adminId);
        return new RatingFixture(fixtureId, playerId, home);
    }

    private record RatingFixture(long fixtureId, long playerId, long teamId) {
    }
}
