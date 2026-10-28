package com.campusfootball;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImportIntegrationTest extends IntegrationTestBase {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void importsTeamsAndPlayersFromCsvInOnePreviewThenCommit() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        MockMultipartFile teams = new MockMultipartFile("file", "teams.csv", "text/csv",
                "球队名称,简称,教练,主场,状态\n导入蓝队,蓝队,李教练,东场,启用\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/manage/import/teams/preview")
                        .file(teams).param("overwrite", "false")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createCount").value(1))
                .andExpect(jsonPath("$.errors.length()").value(0));

        mockMvc.perform(multipart("/api/v1/manage/import/teams/commit")
                        .file(teams).param("overwrite", "false")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        long importedTeamId = jdbc.queryForObject("SELECT id FROM team WHERE name = '导入蓝队'", Long.class);
        MockMultipartFile players = new MockMultipartFile("file", "players.csv", "text/csv",
                "号码,姓名\nA1,王五\n".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/manage/import/players/commit")
                        .file(players).param("teamId", Long.toString(importedTeamId)).param("overwrite", "false")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM player p JOIN team t ON t.id = p.team_id
                WHERE t.name = '导入蓝队' AND p.jersey_no = 'A1'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void previewReturnsRowErrorsWithoutWritingData() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        long teamId = TestApiSupport.createTeam(mockMvc, admin, "预览球队");
        MockMultipartFile players = new MockMultipartFile("file", "players.csv", "text/csv",
                "号码,姓名\n100,王五\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/manage/import/players/preview")
                        .file(players).param("teamId", Long.toString(teamId)).param("overwrite", "false")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].rowNumber").value(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM player WHERE name = '王五'", Integer.class)).isZero();
    }
}