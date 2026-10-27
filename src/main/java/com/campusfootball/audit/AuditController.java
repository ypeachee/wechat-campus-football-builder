package com.campusfootball.audit;

import com.campusfootball.common.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manage")
public class AuditController {
    private final AuditQueryService auditQueryService;
    private final CurrentUser currentUser;

    public AuditController(AuditQueryService auditQueryService, CurrentUser currentUser) {
        this.auditQueryService = auditQueryService;
        this.currentUser = currentUser;
    }

    @GetMapping("/audit-logs")
    public List<AuditLog> auditLogs() {
        return auditQueryService.findVisible(currentUser.require());
    }
}
