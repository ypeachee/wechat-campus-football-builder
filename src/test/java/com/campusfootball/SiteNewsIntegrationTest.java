package com.campusfootball;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
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

class SiteNewsIntegrationTest extends IntegrationTestBase {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCanManagePublicNewsWithUploadedImage() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        byte[] imageBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        MockMultipartFile image = new MockMultipartFile("image", "pitch.png", "image/png", imageBytes);

        JsonNode created = JsonMapper.builder().build().readTree(
                mockMvc.perform(multipart("/api/v1/admin/news")
                                .file(image)
                                .param("title", "Integration news")
                                .param("description", "Uploaded from the admin console")
                                .param("tag", "Match day")
                                .param("publishedAt", "2026-11-06")
                                .param("displayOrder", "5")
                                .param("active", "true")
                                .header("Authorization", "Bearer " + admin))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.title").value("Integration news"))
                        .andExpect(jsonPath("$.imageUrl").isString())
                        .andReturn().getResponse().getContentAsString());

        long newsId = created.get("id").asLong();
        mockMvc.perform(get("/api/v1/news").param("limit", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managed").value(true))
                .andExpect(jsonPath("$.items[0].title").value("Integration news"));

        mockMvc.perform(get("/api/v1/news/" + newsId + "/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(IMAGE_PNG))
                .andExpect(content().bytes(imageBytes));

        MockMultipartFile noImage = new MockMultipartFile("image", new byte[0]);
        mockMvc.perform(multipart("/api/v1/admin/news/" + newsId)
                        .file(noImage)
                        .param("title", "Updated integration news")
                        .param("description", "Updated without replacing the image")
                        .param("tag", "Updated")
                        .param("publishedAt", "2026-11-06")
                        .param("displayOrder", "6")
                        .param("active", "false")
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated integration news"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/news").param("limit", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == " + newsId + ")]").isEmpty());

        mockMvc.perform(delete("/api/v1/admin/news/" + newsId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        assertThat(mockMvc.perform(get("/api/v1/news/" + newsId + "/image"))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void teamManagerCannotManageNews() throws Exception {
        String manager = TestAuthSupport.loginToken(mockMvc, "manager-code");
        mockMvc.perform(get("/api/v1/admin/news")
                        .header("Authorization", "Bearer " + manager))
                .andExpect(status().isForbidden());
    }
}
