package com.campusfootball.rating;

import com.campusfootball.IntegrationTestBase;
import com.campusfootball.TestAuthSupport;
import com.campusfootball.common.api.AppException;
import com.campusfootball.competition.FixtureStatus;
import com.campusfootball.competition.SeasonStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefereeRatingIntegrationTest extends IntegrationTestBase {
    @Autowired
    private RefereeRatingService service;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsSchemaAndRequiresFinishedFixtureWithReferee() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);
        assertThat(tables).contains("fixture_referee_rating");

        long userId = data.findUserId("openid-admin");
        RatingFixture scheduled = fixture(FixtureStatus.SCHEDULED, "马宁");
        assertThatThrownBy(() -> service.submit(userId, scheduled.fixtureId(), 5))
                .isInstanceOf(AppException.class);

        RatingFixture missingReferee = fixture(FixtureStatus.FINISHED, null);
        assertThatThrownBy(() -> service.submit(userId, missingReferee.fixtureId(), 5))
                .isInstanceOf(AppException.class);
    }

    @Test
    void submitsScoresByTenPointScaleAndAllowsOneEdit() {
        long userId = data.findUserId("openid-admin");
        RatingFixture fixture = fixture(FixtureStatus.FINISHED, "马宁");

        RefereeRatingSubmission first = service.submit(userId, fixture.fixtureId(), 5);
        assertThat(first.score()).isEqualTo(10);
        RefereeRatingSummary summary = service.summary(fixture.fixtureId());
        assertThat(summary.refereeName()).isEqualTo("马宁");
        assertThat(summary.averageScore()).isEqualTo(10.0);
        assertThat(summary.ratingCount()).isEqualTo(1);

        RefereeRatingSubmission edited = service.submit(userId, fixture.fixtureId(), 3);
        assertThat(edited.score()).isEqualTo(6);
        assertThatThrownBy(() -> service.submit(userId, fixture.fixtureId(), 4))
                .isInstanceOf(AppException.class);
    }

    @Test
    void refereeChangeCreatesSeparateRatingTarget() {
        long userId = data.findUserId("openid-admin");
        RatingFixture fixture = fixture(FixtureStatus.FINISHED, "马宁");
        service.submit(userId, fixture.fixtureId(), 5);
        jdbc.update("UPDATE fixture SET referee = '张雷' WHERE id = ?", fixture.fixtureId());

        RefereeRatingSubmission second = service.submit(userId, fixture.fixtureId(), 4);
        assertThat(second.score()).isEqualTo(8);
        assertThat(service.summary(fixture.fixtureId()).refereeName()).isEqualTo("张雷");
        assertThat(service.summary(fixture.fixtureId()).ratingCount()).isEqualTo(1);
    }

    @Test
    void exposesPublicSummaryAndProtectsPersonalRatingEndpoints() throws Exception {
        RatingFixture fixture = fixture(FixtureStatus.FINISHED, "傅明");
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");

        mockMvc.perform(put("/api/v1/fixtures/" + fixture.fixtureId() + "/referee-rating")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"stars\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(8));
        mockMvc.perform(get("/api/v1/fixtures/" + fixture.fixtureId() + "/referee-rating"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageScore").value(8.0))
                .andExpect(jsonPath("$.ratingCount").value(1));
        mockMvc.perform(get("/api/v1/fixtures/" + fixture.fixtureId() + "/referee-rating/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/fixtures/" + fixture.fixtureId() + "/referee-rating/me")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rated").value(true))
                .andExpect(jsonPath("$.stars").value(4));
    }

    private RatingFixture fixture(FixtureStatus status, String referee) {
        long admin = data.findUserId("openid-admin");
        long seasonId = data.createSeason("RefereeRating", SeasonStatus.ACTIVE);
        long home = data.createTeam("RefereeHome");
        long away = data.createTeam("RefereeAway");
        data.addSeasonTeam(seasonId, home);
        data.addSeasonTeam(seasonId, away);
        long fixtureId = data.createFixture(seasonId, home, away, status, admin, 1, 0, true);
        jdbc.update("UPDATE fixture SET referee = ? WHERE id = ?", referee, fixtureId);
        return new RatingFixture(fixtureId);
    }

    private record RatingFixture(long fixtureId) {
    }
}
