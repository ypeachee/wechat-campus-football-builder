package com.campusfootball.rating;

import com.campusfootball.IntegrationTestBase;
import com.campusfootball.TestAuthSupport;
import com.campusfootball.competition.FixtureStatus;
import com.campusfootball.competition.SeasonStatus;
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

class RatingPublicApiIntegrationTest extends IntegrationTestBase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aggregatesVisibleRatingsAndHidesUserIdentifiers() throws Exception {
        RatingFixture fixture = fixture();
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        String viewer = TestAuthSupport.loginToken(mockMvc, "viewer-code");

        submit(admin, fixture, 4, "防守稳健");
        submit(viewer, fixture, 5, "补位及时");

        mockMvc.perform(get("/api/v1/fixtures/" + fixture.fixtureId() + "/player-ratings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playerId").value(fixture.playerId()))
                .andExpect(jsonPath("$[0].averageScore").value(9.0))
                .andExpect(jsonPath("$[0].ratingCount").value(2))
                .andExpect(jsonPath("$[0].star4Count").value(1))
                .andExpect(jsonPath("$[0].star5Count").value(1));

        mockMvc.perform(get("/api/v1/fixtures/" + fixture.fixtureId()
                        + "/players/" + fixture.playerId() + "/ratings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.averageScore").value(9.0))
                .andExpect(jsonPath("$.comments.length()").value(2))
                .andExpect(jsonPath("$.comments[0].comment").exists())
                .andExpect(jsonPath("$.comments[0].openid").doesNotExist())
                .andExpect(jsonPath("$.comments[0].userId").doesNotExist());
    }

    @Test
    void protectsPersonalEndpointsAndRejectsSelfAndDuplicateReports() throws Exception {
        RatingFixture fixture = fixture();
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        String viewer = TestAuthSupport.loginToken(mockMvc, "viewer-code");
        long ratingId = submit(admin, fixture, 5, "公开短评");

        mockMvc.perform(get("/api/v1/fixtures/" + fixture.fixtureId()
                        + "/players/" + fixture.playerId() + "/rating/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/fixtures/" + fixture.fixtureId()
                        + "/players/" + fixture.playerId() + "/rating")
                        .contentType(APPLICATION_JSON)
                        .content("{\"stars\":5}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/ratings/" + ratingId + "/report")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"自己的评价\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/ratings/" + ratingId + "/report")
                        .header("Authorization", "Bearer " + viewer)
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"不当内容\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ratings/" + ratingId + "/report")
                        .header("Authorization", "Bearer " + viewer)
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"重复举报\"}"))
                .andExpect(status().isConflict());
    }

    private long submit(String token, RatingFixture fixture, int stars, String comment) throws Exception {
        MvcResult result = mockMvc.perform(put("/api/v1/fixtures/" + fixture.fixtureId()
                        + "/players/" + fixture.playerId() + "/rating")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"stars\":" + stars + ",\"comment\":\"" + comment + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(stars * 2))
                .andReturn();
        JsonNode body = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private RatingFixture fixture() {
        long userId = data.findUserId("openid-admin");
        long seasonId = data.createSeason("RatingApi", SeasonStatus.ACTIVE);
        long home = data.createTeam("RatingApiHome");
        long away = data.createTeam("RatingApiAway");
        data.addSeasonTeam(seasonId, home);
        data.addSeasonTeam(seasonId, away);
        long fixtureId = data.createFixture(seasonId, home, away, FixtureStatus.FINISHED,
                userId, 2, 0, true);
        long playerId = data.createPlayer(home, "RatingApiPlayer", 9);
        data.createAppearance(fixtureId, home, playerId, userId);
        return new RatingFixture(fixtureId, playerId);
    }

    private record RatingFixture(long fixtureId, long playerId) {
    }
}
