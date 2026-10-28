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
public class StageService {
    private final StageMapper stageMapper;
    private final SeasonMapper seasonMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public StageService(StageMapper stageMapper, SeasonMapper seasonMapper,
                        AuthorizationService authorizationService, AuditService auditService) {
        this.stageMapper = stageMapper;
        this.seasonMapper = seasonMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    public List<Stage> findBySeason(long seasonId) {
        if (seasonMapper.findById(seasonId).isEmpty()) {
            throw notFound("赛季不存在");
        }
        return stageMapper.findBySeason(seasonId);
    }

    public Stage get(long id) {
        return stageMapper.findById(id).orElseThrow(() -> notFound("阶段不存在"));
    }

    @Transactional
    public long create(long actorUserId, long seasonId, String name, StageType type, Integer sortOrder,
                       StageStatus status) {
        authorizationService.requireManagementAccess(actorUserId);
        seasonMapper.findById(seasonId).orElseThrow(() -> notFound("赛季不存在"));
        Stage stage = new Stage();
        stage.setSeasonId(seasonId);
        stage.setName(name);
        stage.setType(type);
        stage.setSortOrder(sortOrder == null ? 0 : sortOrder);
        stage.setStatus(status == null ? StageStatus.DRAFT : status);
        stageMapper.insert(stage);
        auditService.record(actorUserId, null, AuditAction.CREATE, "STAGE", stage.getId(), null, stage);
        return stage.getId();
    }

    @Transactional
    public void update(long actorUserId, long id, String name, StageType type, Integer sortOrder, StageStatus status) {
        authorizationService.requireManagementAccess(actorUserId);
        Stage existing = get(id);
        Stage updated = new Stage();
        updated.setId(id);
        updated.setSeasonId(existing.getSeasonId());
        updated.setName(name);
        updated.setType(type);
        updated.setSortOrder(sortOrder == null ? existing.getSortOrder() : sortOrder);
        updated.setStatus(status == null ? existing.getStatus() : status);
        stageMapper.update(updated);
        auditService.record(actorUserId, null, AuditAction.UPDATE, "STAGE", id, existing, updated);
    }

    private AppException notFound(String message) {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }
}
