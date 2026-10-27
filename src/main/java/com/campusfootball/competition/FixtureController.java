package com.campusfootball.competition;

import com.campusfootball.team.Team;
import com.campusfootball.team.TeamService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class FixtureController {
    private final FixtureService fixtureService;
    private final TeamService teamService;
    private final AppearanceService appearanceService;
    private final EventService eventService;

    public FixtureController(FixtureService fixtureService, TeamService teamService,
                             AppearanceService appearanceService, EventService eventService) {
        this.fixtureService = fixtureService;
        this.teamService = teamService;
        this.appearanceService = appearanceService;
        this.eventService = eventService;
    }

    @GetMapping("/fixtures")
    public List<FixtureResponse> fixtures(@RequestParam(required = false) Long seasonId,
                                          @RequestParam(required = false) Long stageId,
                                          @RequestParam(required = false) Long groupId) {
        List<Fixture> fixtures = stageId != null
                ? fixtureService.findPublicByStage(stageId, groupId)
                : fixtureService.findPublicBySeason(seasonId == null ? 0 : seasonId);
        return fixtures.stream().map(FixtureResponse::from).toList();
    }

    @GetMapping("/fixtures/{fixtureId}")
    public FixtureDetailResponse fixture(@PathVariable long fixtureId) {
        Fixture fixture = fixtureService.getPublic(fixtureId);
        Team home = teamService.getTeam(fixture.getHomeTeamId());
        Team away = teamService.getTeam(fixture.getAwayTeamId());
        List<EventResponse> events = eventService.findByFixture(fixtureId).stream().map(EventResponse::from).toList();
        List<AppearanceResponse> appearances = appearanceService.findByFixture(fixtureId).stream()
                .map(AppearanceResponse::from).toList();
        return new FixtureDetailResponse(fixture.getId(), fixture.getSeasonId(), fixture.getRoundNo(),
                TeamRef.from(home), TeamRef.from(away), fixture.getKickoffAt(), fixture.getVenue(),
                fixture.getReferee(), fixture.getStatus(), fixture.getHomeScore(), fixture.getAwayScore(), fixture.isDataLocked(),
                events, appearances);
    }

    public record FixtureResponse(long id, long seasonId, long stageId, Long groupId, Long knockoutTieId,
                                  Integer roundNo, long homeTeamId, long awayTeamId,
                                  LocalDateTime kickoffAt, String venue, String referee, FixtureStatus status,
                                  Integer homeScore, Integer awayScore,
                                  Integer homePenaltyScore, Integer awayPenaltyScore, boolean dataLocked) {
        static FixtureResponse from(Fixture fixture) {
            return new FixtureResponse(fixture.getId(), fixture.getSeasonId(), fixture.getStageId(),
                    fixture.getGroupId(), fixture.getKnockoutTieId(), fixture.getRoundNo(),
                    fixture.getHomeTeamId(), fixture.getAwayTeamId(), fixture.getKickoffAt(), fixture.getVenue(),
                    fixture.getReferee(), fixture.getStatus(), fixture.getHomeScore(), fixture.getAwayScore(),
                    fixture.getHomePenaltyScore(), fixture.getAwayPenaltyScore(), fixture.isDataLocked());
        }
    }

    public record TeamRef(long id, String name, String shortName) {
        static TeamRef from(Team team) {
            return new TeamRef(team.getId(), team.getName(), team.getShortName());
        }
    }

    public record EventResponse(long id, long teamId, long playerId, EventType eventType,
                                Long relatedPlayerId, int minute, int stoppageMinute, String note) {
        static EventResponse from(FixtureEvent event) {
            return new EventResponse(event.getId(), event.getTeamId(), event.getPlayerId(), event.getEventType(),
                    event.getRelatedPlayerId(), event.getMinute(), event.getStoppageMinute(), event.getNote());
        }
    }

    public record AppearanceResponse(long id, long teamId, long playerId, boolean starter, Integer minutesPlayed) {
        static AppearanceResponse from(FixtureAppearance appearance) {
            return new AppearanceResponse(appearance.getId(), appearance.getTeamId(), appearance.getPlayerId(),
                    appearance.isStarter(), appearance.getMinutesPlayed());
        }
    }

    public record FixtureDetailResponse(long id, long seasonId, Integer roundNo, TeamRef homeTeam, TeamRef awayTeam,
                                        LocalDateTime kickoffAt, String venue, String referee, FixtureStatus status,
                                        Integer homeScore, Integer awayScore, boolean dataLocked,
                                        List<EventResponse> events, List<AppearanceResponse> appearances) {
    }
}
