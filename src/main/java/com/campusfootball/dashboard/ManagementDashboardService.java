package com.campusfootball.dashboard;

import com.campusfootball.rating.RatingMapper;
import com.campusfootball.team.AuthorizationService;
import com.campusfootball.user.UserRole;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class ManagementDashboardService {
    private final DashboardMapper dashboardMapper;
    private final RatingMapper ratingMapper;
    private final AuthorizationService authorizationService;
    private final Clock clock;

    public ManagementDashboardService(DashboardMapper dashboardMapper, RatingMapper ratingMapper,
                                      AuthorizationService authorizationService, Clock clock) {
        this.dashboardMapper = dashboardMapper;
        this.ratingMapper = ratingMapper;
        this.authorizationService = authorizationService;
        this.clock = clock;
    }

    public DashboardView get(long actorUserId) {
        UserRole role = authorizationService.currentRole(actorUserId);
        if (role == UserRole.VIEWER) {
            throw new com.campusfootball.common.api.AppException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    com.campusfootball.common.api.ErrorCode.FORBIDDEN,
                    "没有权限查看管理待办");
        }
        boolean allTeams = role == UserRole.ADMIN || role == UserRole.TEAM_MANAGER;
        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        LocalDateTime from = tomorrow.atStartOfDay();
        LocalDateTime until = from.plusDays(1);
        Long pendingRatings = role == UserRole.ADMIN ? ratingMapper.countForModeration("PENDING") : null;
        Long reportedRatings = role == UserRole.ADMIN ? ratingMapper.countForModeration("REPORTED") : null;
        return new DashboardView(pendingRatings, reportedRatings,
                dashboardMapper.countTomorrowFixtures(actorUserId, allTeams, from, until),
                dashboardMapper.countActiveSuspensions(actorUserId, allTeams));
    }

    public record DashboardView(Long pendingRatings, Long reportedRatings,
                                long tomorrowFixtures, long activeSuspensions) {
    }
}