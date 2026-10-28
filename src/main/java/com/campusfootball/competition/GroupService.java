package com.campusfootball.competition;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.team.AuthorizationService;
import com.campusfootball.team.TeamMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GroupService {
    private final GroupMapper groupMapper;
    private final StageMapper stageMapper;
    private final SeasonMapper seasonMapper;
    private final TeamMapper teamMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public GroupService(GroupMapper groupMapper, StageMapper stageMapper, SeasonMapper seasonMapper,
                        TeamMapper teamMapper, AuthorizationService authorizationService, AuditService auditService) {
        this.groupMapper = groupMapper;
        this.stageMapper = stageMapper;
        this.seasonMapper = seasonMapper;
        this.teamMapper = teamMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    public List<CompetitionGroup> findByStage(long stageId) {
        getStage(stageId);
        return groupMapper.findByStage(stageId);
    }

    public CompetitionGroup get(long id) {
        return groupMapper.findById(id).orElseThrow(() -> notFound("小组不存在"));
    }

    public List<GroupTeam> findTeams(long groupId) {
        get(groupId);
        return groupMapper.findTeams(groupId);
    }

    @Transactional
    public long create(long actorUserId, long stageId, String name, Integer sortOrder) {
        authorizationService.requireManagementAccess(actorUserId);
        Stage stage = getStage(stageId);
        if (stage.getType() != StageType.GROUP) {
            throw conflict("小组只能创建在小组赛阶段");
        }
        CompetitionGroup group = new CompetitionGroup();
        group.setStageId(stageId);
        group.setName(name);
        group.setSortOrder(sortOrder == null ? 0 : sortOrder);
        groupMapper.insert(group);
        auditService.record(actorUserId, null, AuditAction.CREATE, "GROUP", group.getId(), null, group);
        return group.getId();
    }

    @Transactional
    public void addTeam(long actorUserId, long groupId, long teamId, Integer seedNo) {
        authorizationService.requireManagementAccess(actorUserId);
        CompetitionGroup group = get(groupId);
        Stage stage = getStage(group.getStageId());
        seasonMapper.findById(stage.getSeasonId()).orElseThrow(() -> notFound("赛季不存在"));
        teamMapper.findById(teamId).orElseThrow(() -> notFound("球队不存在"));
        if (seasonMapper.countSeasonTeam(stage.getSeasonId(), teamId) == 0) {
            throw conflict("球队尚未参加该赛季");
        }
        if (groupMapper.countGroupTeam(groupId, teamId) > 0) {
            throw conflict("球队已在该小组");
        }
        if (groupMapper.countTeamInStage(stage.getId(), teamId) > 0) {
            throw conflict("球队已属于该阶段的其他小组");
        }
        groupMapper.addTeam(groupId, teamId, seedNo);
        auditService.record(actorUserId, teamId, AuditAction.CREATE, "GROUP_TEAM", groupId, null,
                new GroupTeamSnapshot(groupId, teamId, seedNo));
    }

    @Transactional
    public void removeTeam(long actorUserId, long groupId, long teamId) {
        authorizationService.requireManagementAccess(actorUserId);
        get(groupId);
        if (groupMapper.countGroupTeam(groupId, teamId) == 0) {
            throw notFound("小组球队不存在");
        }
        groupMapper.removeTeam(groupId, teamId);
        auditService.record(actorUserId, teamId, AuditAction.DELETE, "GROUP_TEAM", groupId,
                new GroupTeamSnapshot(groupId, teamId, null), null);
    }

    private Stage getStage(long stageId) {
        return stageMapper.findById(stageId).orElseThrow(() -> notFound("阶段不存在"));
    }

    private AppException conflict(String message) {
        return new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }

    private AppException notFound(String message) {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    private record GroupTeamSnapshot(long groupId, long teamId, Integer seedNo) {
    }
}
