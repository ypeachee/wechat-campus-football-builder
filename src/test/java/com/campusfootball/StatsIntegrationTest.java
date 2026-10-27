package com.campusfootball;

import com.campusfootball.competition.EventType;
import com.campusfootball.competition.FixtureStatus;
import com.campusfootball.competition.SeasonStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatsIntegrationTest extends IntegrationTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void scorerAssistAppearanceAndStandingRulesAreApplied() throws Exception {
        Dataset d = seedMainDataset();

        JsonNode scorers = getJson("/api/v1/stats/scorers?seasonId=" + d.seasonId());
        assertThat(row(scorers, "playerId", d.b1()).get("goals").asLong()).isEqualTo(4);
        assertThat(row(scorers, "playerId", d.a1()).get("goals").asLong()).isEqualTo(2);
        assertThat(row(scorers, "playerId", d.c1())).isNull();

        JsonNode assists = getJson("/api/v1/stats/assists?seasonId=" + d.seasonId());
        assertThat(row(assists, "playerId", d.a2()).get("assists").asLong()).isEqualTo(1);

        JsonNode appearances = getJson("/api/v1/stats/appearances?seasonId=" + d.seasonId());
        assertThat(row(appearances, "playerId", d.a1()).get("appearances").asLong()).isEqualTo(2);
        assertThat(row(appearances, "playerId", d.c1()).get("appearances").asLong()).isEqualTo(2);
        assertThat(row(appearances, "playerId", d.d1())).isNull();

        mockMvc.perform(get("/api/v1/standings").param("seasonId", Long.toString(d.seasonId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamId").value(d.teamA()))
                .andExpect(jsonPath("$[0].played").value(2))
                .andExpect(jsonPath("$[0].won").value(1))
                .andExpect(jsonPath("$[0].drawn").value(1))
                .andExpect(jsonPath("$[0].lost").value(0))
                .andExpect(jsonPath("$[0].goalsFor").value(2))
                .andExpect(jsonPath("$[0].goalsAgainst").value(1))
                .andExpect(jsonPath("$[0].goalDifference").value(1))
                .andExpect(jsonPath("$[0].points").value(4))
                .andExpect(jsonPath("$[1].teamId").value(d.teamB()))
                .andExpect(jsonPath("$[1].points").value(3))
                .andExpect(jsonPath("$[2].teamId").value(d.teamC()))
                .andExpect(jsonPath("$[2].points").value(1))
                .andExpect(jsonPath("$[3].teamId").value(d.teamD()))
                .andExpect(jsonPath("$[3].played").value(0))
                .andExpect(jsonPath("$[3].points").value(0));
    }

    @Test
    void publicFixtureDetailIncludesEventsAndAppearancesAndHidesDraft() throws Exception {
        Dataset d = seedMainDataset();

        mockMvc.perform(get("/api/v1/fixtures/" + d.fixture1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeTeam.id").value(d.teamA()))
                .andExpect(jsonPath("$.awayTeam.id").value(d.teamB()))
                .andExpect(jsonPath("$.events.length()").value(4))
                .andExpect(jsonPath("$.appearances.length()").value(3));
        mockMvc.perform(get("/api/v1/fixtures/" + d.draftFixture())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/seasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + d.draftSeason() + ")]").isEmpty());
    }

    @Test
    void standingsTieBreakByGoalDifference() throws Exception {
        long admin = data.findUserId("openid-admin");
        long seasonId = data.createSeason("TieSeason", SeasonStatus.ACTIVE);
        long t1 = data.createTeam("TieA");
        long t2 = data.createTeam("TieB");
        long t3 = data.createTeam("TieC");
        long t4 = data.createTeam("TieD");
        data.addSeasonTeam(seasonId, t1);
        data.addSeasonTeam(seasonId, t2);
        data.addSeasonTeam(seasonId, t3);
        data.addSeasonTeam(seasonId, t4);
        data.createFixture(seasonId, t1, t3, FixtureStatus.FINISHED, admin, 2, 0, true);
        data.createFixture(seasonId, t2, t4, FixtureStatus.FINISHED, admin, 1, 0, true);

        mockMvc.perform(get("/api/v1/standings").param("seasonId", Long.toString(seasonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamId").value(t1))
                .andExpect(jsonPath("$[0].goalDifference").value(2))
                .andExpect(jsonPath("$[1].teamId").value(t2))
                .andExpect(jsonPath("$[1].goalDifference").value(1))
                .andExpect(jsonPath("$[2].teamId").value(t4))
                .andExpect(jsonPath("$[3].teamId").value(t3));
    }

    private Dataset seedMainDataset() {
        long admin = data.findUserId("openid-admin");
        long seasonId = data.createSeason("StatsSeason", SeasonStatus.ACTIVE);
        long draftSeason = data.createSeason("DraftStatsSeason", SeasonStatus.DRAFT);
        long teamA = data.createTeam("StatsA");
        long teamB = data.createTeam("StatsB");
        long teamC = data.createTeam("StatsC");
        long teamD = data.createTeam("StatsD");
        data.addSeasonTeam(seasonId, teamA);
        data.addSeasonTeam(seasonId, teamB);
        data.addSeasonTeam(seasonId, teamC);
        data.addSeasonTeam(seasonId, teamD);

        long a1 = data.createPlayer(teamA, "A1", 9);
        long a2 = data.createPlayer(teamA, "A2", 8);
        long b1 = data.createPlayer(teamB, "B1", 10);
        long c1 = data.createPlayer(teamC, "C1", 11);
        long d1 = data.createPlayer(teamD, "D1", 12);

        long f1 = data.createFixture(seasonId, teamA, teamB, FixtureStatus.FINISHED, admin, 2, 1, true);
        long f2 = data.createFixture(seasonId, teamA, teamC, FixtureStatus.FINISHED, admin, 0, 0, true);
        long f3 = data.createFixture(seasonId, teamB, teamC, FixtureStatus.FINISHED, admin, 3, 0, true);
        long f4 = data.createFixture(seasonId, teamC, teamD, FixtureStatus.CANCELLED, admin, 5, 0, false);
        data.createFixture(seasonId, teamA, teamD, FixtureStatus.SCHEDULED, admin, null, null, false);
        long draftFixture = data.createFixture(seasonId, teamA, teamD, FixtureStatus.DRAFT, admin, null, null, false);

        data.createAppearance(f1, teamA, a1, admin);
        data.createAppearance(f1, teamA, a2, admin);
        data.createAppearance(f1, teamB, b1, admin);
        data.createAppearance(f2, teamA, a1, admin);
        data.createAppearance(f2, teamC, c1, admin);
        data.createAppearance(f3, teamB, b1, admin);
        data.createAppearance(f3, teamC, c1, admin);
        data.createAppearance(f4, teamC, c1, admin);
        data.createAppearance(f4, teamD, d1, admin);

        data.createEvent(f1, teamA, a1, EventType.GOAL, a2, 10, admin);
        data.createEvent(f1, teamA, a1, EventType.GOAL, null, 20, admin);
        data.createEvent(f1, teamB, b1, EventType.GOAL, null, 30, admin);
        data.createEvent(f1, teamB, a1, EventType.OWN_GOAL, null, 40, admin);
        data.createEvent(f3, teamB, b1, EventType.GOAL, null, 10, admin);
        data.createEvent(f3, teamB, b1, EventType.GOAL, null, 20, admin);
        data.createEvent(f3, teamB, b1, EventType.GOAL, null, 30, admin);
        data.createEvent(f4, teamC, c1, EventType.GOAL, null, 10, admin);

        return new Dataset(seasonId, draftSeason, teamA, teamB, teamC, teamD,
                a1, a2, b1, c1, d1, f1, draftFixture);
    }

    private JsonNode getJson(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode row(JsonNode rows, String field, long id) {
        for (JsonNode row : rows) {
            if (row.get(field).asLong() == id) {
                return row;
            }
        }
        return null;
    }

    private record Dataset(long seasonId, long draftSeason, long teamA, long teamB, long teamC, long teamD,
                           long a1, long a2, long b1, long c1, long d1, long fixture1, long draftFixture) {
    }
}

