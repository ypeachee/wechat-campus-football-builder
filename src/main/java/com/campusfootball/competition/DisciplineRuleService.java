package com.campusfootball.competition;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.team.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DisciplineRuleService {
    private final DisciplineRuleMapper disciplineRuleMapper;
    private final CompetitionMapper competitionMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public DisciplineRuleService(DisciplineRuleMapper disciplineRuleMapper, CompetitionMapper competitionMapper,
                                 AuthorizationService authorizationService, AuditService auditService) {
        this.disciplineRuleMapper = disciplineRuleMapper;
        this.competitionMapper = competitionMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    public DisciplineRule get(long actorUserId, long competitionId) {
        authorizationService.requireManagementAccess(actorUserId);
        requireCompetition(competitionId);
        return disciplineRuleMapper.findByCompetitionId(competitionId)
                .orElseGet(() -> DisciplineRule.defaults(competitionId));
    }

    @Transactional
    public DisciplineRule update(long actorUserId, long competitionId, int yellowThreshold,
                                 int redMatches, boolean cardsCrossStages) {
        authorizationService.requireManagementAccess(actorUserId);
        requireCompetition(competitionId);
        if (yellowThreshold < 1 || yellowThreshold > 9 || redMatches < 1 || redMatches > 20) {
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "黄牌停赛阈值或红牌停赛场次不合法");
        }
        DisciplineRule before = get(actorUserId, competitionId);
        DisciplineRule after = new DisciplineRule(competitionId, yellowThreshold, redMatches, cardsCrossStages);
        disciplineRuleMapper.update(after);
        auditService.record(actorUserId, null, AuditAction.UPDATE, "DISCIPLINE_RULE",
                competitionId, before, after);
        return after;
    }

    private void requireCompetition(long competitionId) {
        competitionMapper.findById(competitionId).orElseThrow(() ->
                new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "赛事不存在"));
    }
}