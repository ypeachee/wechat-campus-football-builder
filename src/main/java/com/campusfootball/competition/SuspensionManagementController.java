package com.campusfootball.competition;

import com.campusfootball.common.api.IdResponse;
import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/manage")
public class SuspensionManagementController {
    private final SuspensionService suspensionService;
    private final CurrentUser currentUser;

    public SuspensionManagementController(SuspensionService suspensionService, CurrentUser currentUser) {
        this.suspensionService = suspensionService;
        this.currentUser = currentUser;
    }

    @GetMapping("/teams/{teamId}/suspensions")
    public List<SuspensionService.SuspensionView> teamSuspensions(@PathVariable long teamId,
                                                                  @RequestParam long seasonId) {
        return suspensionService.findByTeam(currentUser.require().userId(), seasonId, teamId);
    }

    @GetMapping("/fixtures/{fixtureId}/suspensions")
    public List<SuspensionService.SuspensionView> fixtureSuspensions(@PathVariable long fixtureId) {
        return suspensionService.findByFixture(currentUser.require().userId(), fixtureId);
    }

    @PostMapping("/suspensions")
    public IdResponse create(@Valid @RequestBody CreateSuspensionRequest request) {
        long id = suspensionService.createManual(currentUser.require().userId(), request.playerId(),
                request.seasonId(), request.matchesTotal(), request.reason());
        return new IdResponse(id);
    }

    @PutMapping("/suspensions/{id}")
    public void update(@PathVariable long id, @Valid @RequestBody UpdateSuspensionRequest request) {
        suspensionService.updateManual(currentUser.require().userId(), id, request.matchesTotal(), request.status());
    }

    public record CreateSuspensionRequest(@NotNull @Min(1) Long playerId,
                                          @NotNull @Min(1) Long seasonId,
                                          @NotNull @Min(1) @Max(20) Integer matchesTotal,
                                          @NotBlank @Size(max = 255) String reason) {
    }

    public record UpdateSuspensionRequest(@NotNull @Min(1) @Max(20) Integer matchesTotal,
                                          @NotNull SuspensionStatus status) {
    }
}