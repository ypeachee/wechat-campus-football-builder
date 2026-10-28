package com.campusfootball.competition;

import com.campusfootball.audit.AuditAction;
import com.campusfootball.audit.AuditService;
import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.team.AuthorizationService;
import com.campusfootball.team.Team;
import com.campusfootball.team.TeamMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class SeasonService {
    private final SeasonMapper seasonMapper;
    private final CompetitionMapper competitionMapper;
    private final StageMapper stageMapper;
    private final FixtureMapper fixtureMapper;
    private final TeamMapper teamMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public SeasonService(SeasonMapper seasonMapper, CompetitionMapper competitionMapper, StageMapper stageMapper,
                         FixtureMapper fixtureMapper, TeamMapper teamMapper,
                         AuthorizationService authorizationService, AuditService auditService) {
        this.seasonMapper = seasonMapper;
        this.competitionMapper = competitionMapper;
        this.stageMapper = stageMapper;
        this.fixtureMapper = fixtureMapper;
        this.teamMapper = teamMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    public List<Season> findPublic(Long competitionId) {
        return competitionId == null ? seasonMapper.findPublic() : seasonMapper.findPublicByCompetition(competitionId);
    }

    public List<Season> findAll(Long competitionId) {
        return competitionId == null ? seasonMapper.findAll() : seasonMapper.findAllByCompetition(competitionId);
    }

    public Season get(long seasonId) {
        return seasonMapper.findById(seasonId).orElseThrow(() -> notFound());
    }

    public List<Team> findTeams(long seasonId) {
        get(seasonId);
        return seasonMapper.findTeamsBySeason(seasonId);
    }

    @Transactional
    public long createSeason(long actorUserId, String name, LocalDate startDate, LocalDate endDate,
                             SeasonStatus status) {
        return createSeason(actorUserId, null, name, startDate, endDate, status);
    }

    @Transactional
    public long createSeason(long actorUserId, Long competitionId, String name, LocalDate startDate,
                             LocalDate endDate, SeasonStatus status) {
        authorizationService.requireManagementAccess(actorUserId);
        Competition competition = competitionId == null
                ? competitionMapper.findFirstByType(CompetitionType.LEAGUE)
                    .orElseThrow(() -> notFound("默认联赛赛事不存在"))
                : competitionMapper.findById(competitionId)
                    .orElseThrow(() -> notFound("赛事不存在"));
        Season season = new Season();
        season.setCompetitionId(competition.getId());
        season.setName(name);
        season.setStartDate(startDate);
        season.setEndDate(endDate);
        season.setStatus(status == null ? SeasonStatus.DRAFT : status);
        seasonMapper.insert(season);
        if (competition.getType() == CompetitionType.LEAGUE) {
            Stage stage = new Stage();
            stage.setSeasonId(season.getId());
            stage.setName("联赛阶段");
            stage.setType(StageType.LEAGUE);
            stage.setSortOrder(0);
            stage.setStatus(status == SeasonStatus.FINISHED ? StageStatus.FINISHED
                    : status == SeasonStatus.ACTIVE ? StageStatus.ACTIVE : StageStatus.DRAFT);
            stageMapper.insert(stage);
        }
        auditService.record(actorUserId, null, AuditAction.CREATE, "SEASON", season.getId(), null, season);
        return season.getId();
    }

    @Transactional
    public void addTeam(long actorUserId, long seasonId, long teamId) {
        authorizationService.requireManagementAccess(actorUserId);
        get(seasonId);
        teamMapper.findById(teamId).orElseThrow(() -> notFound("球队不存在"));
        if (seasonMapper.countSeasonTeam(seasonId, teamId) > 0) {
            throw new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "球队已参加该赛季");
        }
        seasonMapper.addTeam(seasonId, teamId);
        auditService.record(actorUserId, teamId, AuditAction.CREATE, "SEASON_TEAM", seasonId, null,
                new SeasonTeamSnapshot(seasonId, teamId));
    }

    @Transactional
    public void removeTeam(long actorUserId, long seasonId, long teamId) {
        authorizationService.requireManagementAccess(actorUserId);
        get(seasonId);
        if (seasonMapper.countSeasonTeam(seasonId, teamId) == 0) {
            throw notFound("赛季参赛球队不存在");
        }
        if (fixtureMapper.countBySeasonAndTeam(seasonId, teamId) > 0) {
            throw new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, "该球队已有比赛，不能移除");
        }
        seasonMapper.removeTeam(seasonId, teamId);
        auditService.record(actorUserId, teamId, AuditAction.DELETE, "SEASON_TEAM", seasonId,
                new SeasonTeamSnapshot(seasonId, teamId), null);
    }

    private AppException notFound() {
        return notFound("赛季不存在");
    }

    private AppException notFound(String message) {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    private record SeasonTeamSnapshot(long seasonId, long teamId) {
    }
}
