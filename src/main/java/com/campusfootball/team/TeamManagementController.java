package com.campusfootball.team;

import com.campusfootball.common.api.IdResponse;
import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/manage")
public class TeamManagementController {
    private final TeamService teamService;
    private final PlayerService playerService;
    private final CurrentUser currentUser;

    public TeamManagementController(TeamService teamService, PlayerService playerService, CurrentUser currentUser) {
        this.teamService = teamService;
        this.playerService = playerService;
        this.currentUser = currentUser;
    }

    @PutMapping("/teams/{teamId}")
    public void updateTeam(@PathVariable long teamId, @Valid @RequestBody UpdateTeamRequest request) {
        teamService.updateTeam(currentUser.require().userId(), teamId, request.name(), request.shortName(),
                request.logoUrl(), request.coachName(), request.homeVenue(), request.status());
    }

    @PostMapping("/teams/{teamId}/players")
    public IdResponse createPlayer(@PathVariable long teamId, @Valid @RequestBody CreatePlayerRequest request) {
        long id = playerService.createPlayer(currentUser.require().userId(), teamId, request.name(),
                request.jerseyNo(), request.position(), request.birthDate(), request.photoUrl());
        return new IdResponse(id);
    }

    @PutMapping("/players/{playerId}")
    public void updatePlayer(@PathVariable long playerId, @Valid @RequestBody UpdatePlayerRequest request) {
        playerService.updatePlayer(currentUser.require().userId(), playerId, request.name(), request.jerseyNo(),
                request.position(), request.birthDate(), request.photoUrl(), request.status());
    }

    public record UpdateTeamRequest(@NotBlank @Size(max = 100) String name,
                                    @Size(max = 32) String shortName,
                                    @Size(max = 512) String logoUrl,
                                    @Size(max = 64) String coachName,
                                    @Size(max = 128) String homeVenue,
                                    TeamStatus status) {
    }

    public record CreatePlayerRequest(@NotBlank @Size(max = 64) String name,
                                      @Min(0) @jakarta.validation.constraints.Max(999) Integer jerseyNo,
                                      @Size(max = 20) String position,
                                      LocalDate birthDate,
                                      @Size(max = 512) String photoUrl) {
    }

    public record UpdatePlayerRequest(@NotBlank @Size(max = 64) String name,
                                      @Min(0) @jakarta.validation.constraints.Max(999) Integer jerseyNo,
                                      @Size(max = 20) String position,
                                      LocalDate birthDate,
                                      @Size(max = 512) String photoUrl,
                                      PlayerStatus status) {
    }
}
