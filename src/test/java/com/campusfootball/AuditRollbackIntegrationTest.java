package com.campusfootball;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditRollbackIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditService auditService;

    @Test
    void auditFailureRollsBackPlayerUpdate() throws Exception {
        long teamId = data.createTeam("RollbackTeam");
        long playerId = data.createPlayer(teamId, "OriginalPlayer", 10);
        String originalName = jdbc.queryForObject("SELECT name FROM player WHERE id = ?", String.class, playerId);
        String admin = TestAuthSupport.loginToken(mockMvc, "admin-code");

        doThrow(new IllegalStateException("audit failed"))
                .when(auditService)
                .record(anyLong(), any(), any(AuditAction.class), anyString(), anyLong(), any(), any());

        mockMvc.perform(put("/api/v1/manage/players/" + playerId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"ChangedPlayer\",\"jerseyNo\":10,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForObject("SELECT name FROM player WHERE id = ?", String.class, playerId))
                .isEqualTo(originalName);
    }
}
