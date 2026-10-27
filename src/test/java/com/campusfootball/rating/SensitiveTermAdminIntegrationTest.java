package com.campusfootball.rating;

import com.campusfootball.IntegrationTestBase;
import com.campusfootball.TestAuthSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SensitiveTermAdminIntegrationTest extends IntegrationTestBase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminCanCreateUpdateDisableDeleteAndImportTerms() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        String viewer = TestAuthSupport.loginToken(mockMvc, "viewer-code");

        mockMvc.perform(get("/api/v1/admin/sensitive-terms").header("Authorization", "Bearer " + viewer))
                .andExpect(status().isForbidden());

        MvcResult created = mockMvc.perform(post("/api/v1/admin/sensitive-terms")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"term\":\"管理员词条\",\"category\":\"测试\",\"action\":\"REVIEW\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long id = OBJECT_MAPPER.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/admin/sensitive-terms")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"term\":\"管理员词条\",\"category\":\"测试\",\"action\":\"BLOCK\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/admin/sensitive-terms/" + id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"category\":\"广告\",\"action\":\"BLOCK\",\"enabled\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/sensitive-terms").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].action").value("BLOCK"))
                .andExpect(jsonPath("$[0].enabled").value(false));

        mockMvc.perform(post("/api/v1/admin/sensitive-terms/import")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"terms\":[\"批量词一\",\"批量词二\",\"批量词一\"],\"action\":\"REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(2));

        mockMvc.perform(delete("/api/v1/admin/sensitive-terms/" + id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        Integer auditCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM audit_log WHERE entity_type IN ('SENSITIVE_TERM', 'SENSITIVE_TERM_IMPORT')
                """, Integer.class);
        assertThat(auditCount).isGreaterThanOrEqualTo(4);
    }

    @Test
    void rejectsImportOverLimit() throws Exception {
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");
        List<String> terms = Collections.nCopies(5001, "重复词");
        String body = OBJECT_MAPPER.writeValueAsString(java.util.Map.of(
                "terms", terms, "action", "REVIEW"));

        mockMvc.perform(post("/api/v1/admin/sensitive-terms/import")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
