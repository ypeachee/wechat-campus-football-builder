package com.campusfootball.team;

import com.campusfootball.common.api.IdResponse;
import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminTeamController {
    private final TeamService teamService;
    private final PlayerService playerService;
    private final TeamManagerService teamManagerService;
    private final CurrentUser currentUser;

    public AdminTeamController(TeamService teamService, PlayerService playerService,
                               TeamManagerService teamManagerService, CurrentUser currentUser) {
        this.teamService = teamService;
        this.playerService = playerService;
        this.teamManagerService = teamManagerService;
        this.currentUser = currentUser;
    }

    @PostMapping("/teams")
    public IdResponse createTeam(@Valid @RequestBody CreateTeamRequest request) {
        long id = teamService.createTeam(currentUser.require().userId(), request.name(), request.shortName(),
                request.logoUrl(), request.coachName(), request.homeVenue());
        return new IdResponse(id);
    }

    @PutMapping("/teams/{teamId}")
    public void updateTeam(@PathVariable long teamId, @Valid @RequestBody UpdateTeamRequest request) {
        teamService.updateTeam(currentUser.require().userId(), teamId, request.name(), request.shortName(),
                request.logoUrl(), request.coachName(), request.homeVenue(), request.status());
    }

    @GetMapping("/teams")
    public List<TeamController.TeamResponse> teams() {
        return teamService.findAllTeams().stream().map(TeamController.TeamResponse::from).toList();
    }

    @GetMapping("/teams/{teamId}/players")
    public List<TeamController.PlayerResponse> players(@PathVariable long teamId) {
        teamService.getTeam(teamId);
        return playerService.findAllByTeam(teamId).stream().map(TeamController.PlayerResponse::from).toList();
    }

    @GetMapping("/players/{playerId}")
    public TeamController.PlayerResponse player(@PathVariable long playerId) {
        return TeamController.PlayerResponse.from(playerService.getPlayer(playerId));
    }

    @PostMapping("/teams/{teamId}/managers")
    public void grantManager(@PathVariable long teamId, @Valid @RequestBody GrantManagerRequest request) {
        teamManagerService.grant(currentUser.require().userId(), teamId, request.userId());
    }

    @DeleteMapping("/teams/{teamId}/managers/{userId}")
    public void revokeManager(@PathVariable long teamId, @PathVariable long userId) {
        teamManagerService.revoke(currentUser.require().userId(), teamId, userId);
    }

    public record CreateTeamRequest(@NotBlank @Size(max = 100) String name,
                                    @Size(max = 32) String shortName,
                                    @Size(max = 512) String logoUrl,
                                    @Size(max = 64) String coachName,
                                    @Size(max = 128) String homeVenue) {
    }

    public record UpdateTeamRequest(@NotBlank @Size(max = 100) String name,
                                    @Size(max = 32) String shortName,
                                    @Size(max = 512) String logoUrl,
                                    @Size(max = 64) String coachName,
                                    @Size(max = 128) String homeVenue,
                                    TeamStatus status) {
    }

    public record GrantManagerRequest(@NotNull @Min(1) Long userId) {
    }
}
