package com.campusfootball.competition;

import com.campusfootball.common.api.IdResponse;
import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/admin")
public class BracketAdminController {
    private final BracketService bracketService;
    private final CurrentUser currentUser;

    public BracketAdminController(BracketService bracketService, CurrentUser currentUser) {
        this.bracketService = bracketService;
        this.currentUser = currentUser;
    }

    @GetMapping("/brackets")
    public BracketService.BracketView bracket(@RequestParam long stageId) {
        return bracketService.getBracket(stageId);
    }

    @PostMapping("/stages/{stageId}/rounds")
    public IdResponse createRound(@PathVariable long stageId, @Valid @RequestBody RoundRequest request) {
        return new IdResponse(bracketService.createRound(currentUser.require().userId(), stageId,
                request.roundNo(), request.name(), request.sortOrder()));
    }

    @PostMapping("/stages/{stageId}/ties")
    public IdResponse createTie(@PathVariable long stageId, @Valid @RequestBody TieRequest request) {
        return new IdResponse(bracketService.createTie(currentUser.require().userId(), stageId,
                request.roundId(), request.bracketPosition(), request.homeTeamId(), request.awayTeamId(),
                request.nextTieId(), request.nextSlot(), request.thirdPlace()));
    }

    @PostMapping("/stages/{stageId}/seeds")
    public void saveSeed(@PathVariable long stageId, @Valid @RequestBody SeedRequest request) {
        bracketService.saveSeed(currentUser.require().userId(), stageId, request.groupId(),
                request.sourceRank(), request.targetTieId(), request.targetSlot());
    }

    @PostMapping("/stages/{stageId}/generate-bracket")
    public BracketService.BracketView generate(@PathVariable long stageId,
                                                @Valid @RequestBody GenerateBracketRequest request) {
        return bracketService.generateBracket(currentUser.require().userId(), stageId,
                request.groupStageId(), request.qualifiersPerGroup(), request.thirdPlace());
    }

    @PostMapping("/ties/{tieId}/fixture")
    public IdResponse createFixture(@PathVariable long tieId, @Valid @RequestBody TieFixtureRequest request) {
        return new IdResponse(bracketService.createTieFixture(currentUser.require().userId(), tieId,
                request.kickoffAt(), request.venue(), request.referee()));
    }

    public record RoundRequest(@Min(1) int roundNo, @NotBlank @Size(max = 64) String name, int sortOrder) {}
    public record TieRequest(@NotNull Long roundId, @Min(1) int bracketPosition,
                             Long homeTeamId, Long awayTeamId, Long nextTieId,
                             BracketSlot nextSlot, boolean thirdPlace) {}
    public record SeedRequest(@NotNull Long groupId, @Min(1) int sourceRank,
                              @NotNull Long targetTieId, @NotNull BracketSlot targetSlot) {}
    public record GenerateBracketRequest(@NotNull Long groupStageId, @Min(1) int qualifiersPerGroup,
                                         boolean thirdPlace) {}
    public record TieFixtureRequest(LocalDateTime kickoffAt, @Size(max = 128) String venue,
                                    @Size(max = 64) String referee) {}
}
