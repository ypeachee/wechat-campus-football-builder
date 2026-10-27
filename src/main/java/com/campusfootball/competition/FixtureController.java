package com.campusfootball.competition;

import com.campusfootball.common.api.AppException;
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

    public FixtureController(FixtureService fixtureService) {
        this.fixtureService = fixtureService;
    }

    @GetMapping("/fixtures")
    public List<FixtureResponse> fixtures(@RequestParam long seasonId) {
        return fixtureService.findPublicBySeason(seasonId).stream().map(FixtureResponse::from).toList();
    }

    public record FixtureResponse(long id, long seasonId, Integer roundNo, long homeTeamId, long awayTeamId,
                                  LocalDateTime kickoffAt, String venue, FixtureStatus status,
                                  Integer homeScore, Integer awayScore, boolean dataLocked) {
        static FixtureResponse from(Fixture fixture) {
            return new FixtureResponse(fixture.getId(), fixture.getSeasonId(), fixture.getRoundNo(),
                    fixture.getHomeTeamId(), fixture.getAwayTeamId(), fixture.getKickoffAt(), fixture.getVenue(),
                    fixture.getStatus(), fixture.getHomeScore(), fixture.getAwayScore(), fixture.isDataLocked());
        }
    }
}
