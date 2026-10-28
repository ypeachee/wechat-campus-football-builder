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

    public AuditQueryService(AuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    public List<AuditLog> findVisible(UserPrincipal principal) {
        if (principal.role() != UserRole.ADMIN) {
            throw new AppException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "没有权限查看审计日志");
        }
        return auditMapper.findAll();
    }
}