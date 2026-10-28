package com.campusfootball.competition;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.team.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CompetitionService {
    private final CompetitionMapper competitionMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public CompetitionService(CompetitionMapper competitionMapper, AuthorizationService authorizationService,
                              AuditService auditService) {
        this.competitionMapper = competitionMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    public List<Competition> findActive() { return competitionMapper.findActive(); }
    public List<Competition> findAll() { return competitionMapper.findAll(); }
    public Competition get(long id) {
        return competitionMapper.findById(id).orElseThrow(() ->
                new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "赛事不存在"));
    }

    @Transactional
    public long create(long actorUserId, String name, CompetitionType type, String description) {
        authorizationService.requireManagementAccess(actorUserId);
        Competition competition = new Competition();
        competition.setName(name);
        competition.setType(type);
        competition.setDescription(description);
        competition.setStatus(CompetitionStatus.ACTIVE);
        competitionMapper.insert(competition);
        auditService.record(actorUserId, null, AuditAction.CREATE, "COMPETITION", competition.getId(), null, competition);
        return competition.getId();
    }

    @Transactional
    public void update(long actorUserId, long id, String name, CompetitionType type,
                       String description, CompetitionStatus status) {
        authorizationService.requireManagementAccess(actorUserId);
        Competition existing = get(id);
        Competition updated = new Competition();
        updated.setId(id);
        updated.setName(name);
        updated.setType(type);
        updated.setDescription(description);
        updated.setStatus(status == null ? existing.getStatus() : status);
        competitionMapper.update(updated);
        auditService.record(actorUserId, null, AuditAction.UPDATE, "COMPETITION", id, existing, updated);
    }
}
