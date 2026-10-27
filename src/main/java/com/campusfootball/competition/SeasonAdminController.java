package com.campusfootball.competition;

import com.campusfootball.common.api.IdResponse;
import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class SeasonAdminController {
    private final SeasonService seasonService;
    private final CurrentUser currentUser;

    public SeasonAdminController(SeasonService seasonService, CurrentUser currentUser) {
        this.seasonService = seasonService;
        this.currentUser = currentUser;
    }

    @PostMapping("/seasons")
    public IdResponse createSeason(@Valid @RequestBody CreateSeasonRequest request) {
        long id = seasonService.createSeason(currentUser.require().userId(), request.name(),
                request.startDate(), request.endDate(), request.status());
        return new IdResponse(id);
    }

    @GetMapping("/seasons")
    public List<SeasonController.SeasonResponse> seasons() {
        return seasonService.findAll().stream().map(SeasonController.SeasonResponse::from).toList();
    }

    @PostMapping("/seasons/{seasonId}/teams/{teamId}")
    public void addTeam(@PathVariable long seasonId, @PathVariable long teamId) {
        seasonService.addTeam(currentUser.require().userId(), seasonId, teamId);
    }

    @DeleteMapping("/seasons/{seasonId}/teams/{teamId}")
    public void removeTeam(@PathVariable long seasonId, @PathVariable long teamId) {
        seasonService.removeTeam(currentUser.require().userId(), seasonId, teamId);
    }

    public record CreateSeasonRequest(@NotBlank @Size(max = 64) String name,
                                      LocalDate startDate,
                                      LocalDate endDate,
                                      @NotNull SeasonStatus status) {
    }
}
