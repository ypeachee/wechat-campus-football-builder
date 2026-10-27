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
    private final FixtureMapper fixtureMapper;
    private final TeamMapper teamMapper;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public SeasonService(SeasonMapper seasonMapper, FixtureMapper fixtureMapper, TeamMapper teamMapper,
                         AuthorizationService authorizationService, AuditService auditService) {
        this.seasonMapper = seasonMapper;
        this.fixtureMapper = fixtureMapper;
        this.teamMapper = teamMapper;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    public List<Season> findPublic() {
        return seasonMapper.findPublic();
    }

    public List<Season> findAll() {
        return seasonMapper.findAll();
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
        authorizationService.requireAdmin(actorUserId);
        Season season = new Season();
        season.setName(name);
        season.setStartDate(startDate);
        season.setEndDate(endDate);
        season.setStatus(status == null ? SeasonStatus.DRAFT : status);
        seasonMapper.insert(season);
        auditService.record(actorUserId, null, AuditAction.CREATE, "SEASON", season.getId(), null, season);
        return season.getId();
    }

    @Transactional
    public void addTeam(long actorUserId, long seasonId, long teamId) {
        authorizationService.requireAdmin(actorUserId);
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
        authorizationService.requireAdmin(actorUserId);
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
