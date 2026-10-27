package com.campusfootball;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeasonFixtureIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCreatesSeasonAndDraftFixtureHiddenFromPublic() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long seasonId = TestApiSupport.createSeason(mockMvc, admin, "ActiveSeason", "ACTIVE");
        long home = TestApiSupport.createTeam(mockMvc, admin, "Home");
        long away = TestApiSupport.createTeam(mockMvc, admin, "Away");
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, home);
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, away);
        long fixtureId = TestApiSupport.createFixture(mockMvc, admin, seasonId, home, away);

        mockMvc.perform(get("/api/v1/seasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(seasonId));
        mockMvc.perform(get("/api/v1/fixtures").param("seasonId", Long.toString(seasonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/v1/admin/fixtures").param("seasonId", Long.toString(seasonId))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(fixtureId))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
    }

    @Test
    void draftSeasonHiddenFromPublicButVisibleToAdmin() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long seasonId = TestApiSupport.createSeason(mockMvc, admin, "DraftSeason", "DRAFT");

        mockMvc.perform(get("/api/v1/seasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/v1/admin/seasons").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(seasonId))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
    }

    @Test
    void rejectsDuplicateSeasonTeamAndInvalidFixtureTeams() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long seasonId = TestApiSupport.createSeason(mockMvc, admin, "ValidationSeason", "ACTIVE");
        long teamA = TestApiSupport.createTeam(mockMvc, admin, "TeamA");
        long teamB = TestApiSupport.createTeam(mockMvc, admin, "TeamB");
        long outside = TestApiSupport.createTeam(mockMvc, admin, "Outside");
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, teamA);
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, teamB);

        mockMvc.perform(post("/api/v1/admin/seasons/" + seasonId + "/teams/" + teamA)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/admin/fixtures")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"seasonId\":" + seasonId + ",\"homeTeamId\":" + teamA + ",\"awayTeamId\":" + outside + "}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/admin/fixtures")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"seasonId\":" + seasonId + ",\"homeTeamId\":" + teamA + ",\"awayTeamId\":" + teamA + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void statusTransitionsFollowAllowedGraph() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long seasonId = TestApiSupport.createSeason(mockMvc, admin, "TransitionSeason", "ACTIVE");
        long home = TestApiSupport.createTeam(mockMvc, admin, "TransHome");
        long away = TestApiSupport.createTeam(mockMvc, admin, "TransAway");
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, home);
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, away);
        long fixtureId = TestApiSupport.createFixture(mockMvc, admin, seasonId, home, away);

        mockMvc.perform(put("/api/v1/admin/fixtures/" + fixtureId + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"LIVE\"}"))
                .andExpect(status().isConflict());

        TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "SCHEDULED");
        TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "LIVE");

        mockMvc.perform(put("/api/v1/admin/fixtures/" + fixtureId + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"FINISHED\"}"))
                .andExpect(status().isConflict());

        TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "CANCELLED");
        mockMvc.perform(put("/api/v1/admin/fixtures/" + fixtureId + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"SCHEDULED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void finishLocksFixtureAndScoreWritesRequireUnlock() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long seasonId = TestApiSupport.createSeason(mockMvc, admin, "FinishSeason", "ACTIVE");
        long home = TestApiSupport.createTeam(mockMvc, admin, "FinishHome");
        long away = TestApiSupport.createTeam(mockMvc, admin, "FinishAway");
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, home);
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, away);
        long fixtureId = TestApiSupport.createFixture(mockMvc, admin, seasonId, home, away);

        mockMvc.perform(post("/api/v1/admin/fixtures/" + fixtureId + "/finish")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"homeScore\":1,\"awayScore\":0}"))
                .andExpect(status().isConflict());

        TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "SCHEDULED");
        TestApiSupport.changeStatus(mockMvc, admin, fixtureId, "LIVE");
        TestApiSupport.finish(mockMvc, admin, fixtureId, 1, 0);

        assertThat(jdbc.queryForObject("SELECT status FROM fixture WHERE id = ?", String.class, fixtureId))
                .isEqualTo("FINISHED");
        assertThat(jdbc.queryForObject("SELECT data_locked FROM fixture WHERE id = ?", Boolean.class, fixtureId))
                .isTrue();

        mockMvc.perform(put("/api/v1/admin/fixtures/" + fixtureId + "/score")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"homeScore\":2,\"awayScore\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FIXTURE_LOCKED"));

        mockMvc.perform(put("/api/v1/admin/fixtures/" + fixtureId + "/lock")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"locked\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/admin/fixtures/" + fixtureId + "/score")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"homeScore\":2,\"awayScore\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/fixtures/" + fixtureId + "/finish")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/fixtures/" + fixtureId + "/score")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"homeScore\":-1,\"awayScore\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotRemoveSeasonTeamReferencedByFixture() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long seasonId = TestApiSupport.createSeason(mockMvc, admin, "RemoveSeason", "ACTIVE");
        long home = TestApiSupport.createTeam(mockMvc, admin, "RemoveHome");
        long away = TestApiSupport.createTeam(mockMvc, admin, "RemoveAway");
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, home);
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, away);
        TestApiSupport.createFixture(mockMvc, admin, seasonId, home, away);

        mockMvc.perform(delete("/api/v1/admin/seasons/" + seasonId + "/teams/" + home)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());
    }

    @Test
    void teamManagerCannotAccessAdminFixtureEndpoints() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = TestApiSupport.createTeam(mockMvc, admin, "ManagerTeam");
        var managerLogin = TestAuthSupport.login(mockMvc, "manager-code");
        mockMvc.perform(post("/api/v1/admin/teams/" + teamId + "/managers")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":" + managerLogin.get("userId").asLong() + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/fixtures?seasonId=1")
                        .header("Authorization", "Bearer " + managerLogin.get("token").asText()))
                .andExpect(status().isForbidden());
    }

    @Test
    void seasonFixtureOperationsCreateAuditRows() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long seasonId = TestApiSupport.createSeason(mockMvc, admin, "AuditSeason", "ACTIVE");
        long home = TestApiSupport.createTeam(mockMvc, admin, "AuditHome");
        long away = TestApiSupport.createTeam(mockMvc, admin, "AuditAway");
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, home);
        TestApiSupport.addSeasonTeam(mockMvc, admin, seasonId, away);
        TestApiSupport.createFixture(mockMvc, admin, seasonId, home, away);

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE entity_type IN ('SEASON','SEASON_TEAM','FIXTURE')",
                Integer.class);
        assertThat(rows).isGreaterThanOrEqualTo(4);
    }
}

