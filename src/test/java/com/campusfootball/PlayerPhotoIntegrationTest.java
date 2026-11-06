package com.campusfootball;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.IMAGE_PNG;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlayerPhotoIntegrationTest extends IntegrationTestBase {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCanUploadAndRemovePlayerPhoto() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = TestApiSupport.createTeam(mockMvc, admin, "Photo Team");
        long playerId = TestApiSupport.createPlayer(mockMvc, admin, teamId, "Photo Player", 77);
        byte[] imageBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 9, 8, 7};

        mockMvc.perform(multipart("/api/v1/manage/players/" + playerId + "/photo")
                        .file(new MockMultipartFile("image", "player.png", "image/png", imageBytes))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/players/" + playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value("/api/v1/players/" + playerId + "/photo"));

        mockMvc.perform(get("/api/v1/players/" + playerId + "/photo"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(IMAGE_PNG))
                .andExpect(content().bytes(imageBytes));

        mockMvc.perform(delete("/api/v1/manage/players/" + playerId + "/photo")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        assertThat(mockMvc.perform(get("/api/v1/players/" + playerId + "/photo"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void teamLeaderCannotUploadPhotoForAnotherTeam() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = TestApiSupport.createTeam(mockMvc, admin, "Other Photo Team");
        long playerId = TestApiSupport.createPlayer(mockMvc, admin, teamId, "Other Player", 78);
        String leader = TestAuthSupport.loginToken(mockMvc, "leader-code");

        mockMvc.perform(multipart("/api/v1/manage/players/" + playerId + "/photo")
                        .file(new MockMultipartFile("image", "player.png", "image/png", new byte[]{1, 2, 3}))
                        .header("Authorization", "Bearer " + leader))
                .andExpect(status().isForbidden());
    }
}
