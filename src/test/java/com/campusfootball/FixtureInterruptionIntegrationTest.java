package com.campusfootball;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FixtureInterruptionIntegrationTest extends IntegrationTestBase {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void liveFixtureCanBeMarkedInterrupted() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long home = TestApiSupport.createTeam(mockMvc, admin, "Interrupted Home");
        long away = TestApiSupport.createTeam(mockMvc, admin, "Interrupted Away");
        long seasonId = TestApiSupport.createSeason(mockMvc, admin, "Interrupted Season", "ACTIVE");
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, home);
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, away);
        long fixtureId = TestApiSupport.createFixture(mockMvc, admin, seasonId, home, away);
        TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "SCHEDULED");
        TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "LIVE");
        TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "INTERRUPTED");

        mockMvc.perform(get("/api/v1/fixtures/" + fixtureId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERRUPTED"));
    }
}
