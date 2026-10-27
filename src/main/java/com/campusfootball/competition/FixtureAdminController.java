package com.campusfootball.competition;

import com.campusfootball.common.api.IdResponse;
import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class FixtureAdminController {
    private final FixtureService fixtureService;
    private final CurrentUser currentUser;

    public FixtureAdminController(FixtureService fixtureService, CurrentUser currentUser) {
        this.fixtureService = fixtureService;
        this.currentUser = currentUser;
    }

    @GetMapping("/fixtures")
    public List<FixtureController.FixtureResponse> fixtures(@RequestParam(required = false) Long seasonId,
                                                            @RequestParam(required = false) Long stageId,
                                                            @RequestParam(required = false) Long groupId) {
        List<Fixture> fixtures = stageId != null
                ? fixtureService.findAllByStage(stageId, groupId)
                : fixtureService.findAllBySeason(seasonId == null ? 0 : seasonId);
        return fixtures.stream().map(FixtureController.FixtureResponse::from).toList();
    }

    @GetMapping("/fixtures/{fixtureId}")
    public FixtureController.FixtureResponse fixture(@PathVariable long fixtureId) {
        return FixtureController.FixtureResponse.from(fixtureService.get(fixtureId));
    }

    @PostMapping("/fixtures")
    public IdResponse createFixture(@Valid @RequestBody CreateFixtureRequest request) {
        long id = fixtureService.createFixture(currentUser.require().userId(), request.seasonId(), request.stageId(),
                request.groupId(), request.roundNo(), request.homeTeamId(), request.awayTeamId(),
                request.kickoffAt(), request.venue());
        return new IdResponse(id);
    }

    @PutMapping("/fixtures/{fixtureId}")
    public void updateSchedule(@PathVariable long fixtureId, @Valid @RequestBody UpdateFixtureRequest request) {
        fixtureService.updateSchedule(currentUser.require().userId(), fixtureId, request.seasonId(), request.stageId(),
                request.groupId(), request.roundNo(), request.homeTeamId(), request.awayTeamId(),
                request.kickoffAt(), request.venue());
    }

    @PutMapping("/fixtures/{fixtureId}/status")
    public void changeStatus(@PathVariable long fixtureId, @Valid @RequestBody ChangeStatusRequest request) {
        fixtureService.changeStatus(currentUser.require().userId(), fixtureId, request.status());
    }

    @PutMapping("/fixtures/{fixtureId}/score")
    public void updateScore(@PathVariable long fixtureId, @Valid @RequestBody ScoreRequest request) {
        fixtureService.updateScore(currentUser.require().userId(), fixtureId, request.homeScore(), request.awayScore(),
                request.homePenaltyScore(), request.awayPenaltyScore());
    }

    @PostMapping("/fixtures/{fixtureId}/finish")
    public void finish(@PathVariable long fixtureId, @Valid @RequestBody ScoreRequest request) {
        fixtureService.finish(currentUser.require().userId(), fixtureId, request.homeScore(), request.awayScore(),
                request.homePenaltyScore(), request.awayPenaltyScore());
    }

    @PutMapping("/fixtures/{fixtureId}/lock")
    public void setLocked(@PathVariable long fixtureId, @Valid @RequestBody LockRequest request) {
        fixtureService.setLocked(currentUser.require().userId(), fixtureId, request.locked());
    }

    public record CreateFixtureRequest(@NotNull Long seasonId,
                                       Long stageId,
                                       Long groupId,
                                       @Min(1) Integer roundNo,
                                       @NotNull Long homeTeamId,
                                       @NotNull Long awayTeamId,
                                       LocalDateTime kickoffAt,
                                       @Size(max = 128) String venue) {
    }

    public record UpdateFixtureRequest(@NotNull Long seasonId,
                                       Long stageId,
                                       Long groupId,
                                       @Min(1) Integer roundNo,
                                       @NotNull Long homeTeamId,
                                       @NotNull Long awayTeamId,
                                       LocalDateTime kickoffAt,
                                       @Size(max = 128) String venue) {
    }

    public record ChangeStatusRequest(@NotNull FixtureStatus status) {
    }

    public record ScoreRequest(@NotNull @Min(0) Integer homeScore,
                               @NotNull @Min(0) Integer awayScore,
                               @Min(0) Integer homePenaltyScore,
                               @Min(0) Integer awayPenaltyScore) {
    }

    public record LockRequest(@NotNull Boolean locked) {
    }
}
