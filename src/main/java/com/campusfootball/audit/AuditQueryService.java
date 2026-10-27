package com.campusfootball.audit;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.common.security.UserPrincipal;
import com.campusfootball.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditQueryService {
    private final AuditMapper auditMapper;
    private final com.campusfootball.team.AuthorizationService authorizationService;

    public AuditQueryService(AuditMapper auditMapper,
                             com.campusfootball.team.AuthorizationService authorizationService) {
        this.auditMapper = auditMapper;
        this.authorizationService = authorizationService;
    }

    public List<AuditLog> findVisible(UserPrincipal principal) {
        if (principal.role() == UserRole.ADMIN) {
            return auditMapper.findAll();
        }
        if (principal.role() != UserRole.TEAM_MANAGER) {
            throw new AppException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "没有权限查看审计日志");
        }
        List<Long> teamIds = authorizationService.managedTeamIds(principal.userId());
        return teamIds.isEmpty() ? List.of() : auditMapper.findForTeams(teamIds);
    }
}
