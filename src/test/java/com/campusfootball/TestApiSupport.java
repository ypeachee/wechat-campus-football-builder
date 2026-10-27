package com.campusfootball;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class TestApiSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TestApiSupport() {
    }

    public static long createTeam(MockMvc mockMvc, String token, String name) throws Exception {
        return id(mockMvc.perform(post("/api/v1/admin/teams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn());
    }

    public static long createSeason(MockMvc mockMvc, String token, String name, String status) throws Exception {
        return id(mockMvc.perform(post("/api/v1/admin/seasons")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk())
                .andReturn());
    }

    public static void addSeasonTeam(MockMvc mockMvc, String token, long seasonId, long teamId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/seasons/" + seasonId + "/teams/" + teamId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    public static long createFixture(MockMvc mockMvc, String token, long seasonId, long homeTeamId, long awayTeamId)
            throws Exception {
        return id(mockMvc.perform(post("/api/v1/admin/fixtures")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"seasonId":%d,"homeTeamId":%d,"awayTeamId":%d}
                                """.formatted(seasonId, homeTeamId, awayTeamId)))
                .andExpect(status().isOk())
                .andReturn());
    }

    public static void changeStatus(MockMvc mockMvc, String token, long fixtureId, String status) throws Exception {
        mockMvc.perform(put("/api/v1/admin/fixtures/" + fixtureId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }


    public static long createPlayer(MockMvc mockMvc, String token, long teamId, String name, int jerseyNo)
            throws Exception {
        return id(mockMvc.perform(post("/api/v1/manage/teams/" + teamId + "/players")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"jerseyNo\":" + jerseyNo + "}"))
                .andExpect(status().isOk())
                .andReturn());
    }

    public static long addAppearance(MockMvc mockMvc, String token, long fixtureId, long teamId,
                                     long playerId, boolean starter, Integer minutes) throws Exception {
        String minutesJson = minutes == null ? "null" : Integer.toString(minutes);
        return id(mockMvc.perform(post("/api/v1/manage/fixtures/" + fixtureId + "/appearances")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"teamId\":" + teamId + ",\"playerId\":" + playerId
                                + ",\"isStarter\":" + starter + ",\"minutesPlayed\":" + minutesJson + "}"))
                .andExpect(status().isOk())
                .andReturn());
    }

    public static void setLocked(MockMvc mockMvc, String token, long fixtureId, boolean locked) throws Exception {
        mockMvc.perform(put("/api/v1/admin/fixtures/" + fixtureId + "/lock")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"locked\":" + locked + "}"))
                .andExpect(status().isOk());
    }

    public static void finish(MockMvc mockMvc, String token, long fixtureId, int homeScore, int awayScore)
            throws Exception {
        mockMvc.perform(post("/api/v1/admin/fixtures/" + fixtureId + "/finish")
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"homeScore\":" + homeScore + ",\"awayScore\":" + awayScore + "}"))
                .andExpect(status().isOk());
    }

    private static long id(MvcResult result) throws Exception {
        return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
