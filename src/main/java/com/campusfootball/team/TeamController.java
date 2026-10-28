package com.campusfootball.team;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class TeamController {
    private final TeamService teamService;
    private final PlayerService playerService;

    public TeamController(TeamService teamService, PlayerService playerService) {
        this.teamService = teamService;
        this.playerService = playerService;
    }

    @GetMapping("/teams")
    public List<TeamResponse> teams() {
        return teamService.findActiveTeams().stream().map(TeamResponse::from).toList();
    }

    @GetMapping("/teams/{teamId}")
    public TeamResponse team(@PathVariable long teamId) {
        return TeamResponse.from(teamService.getActiveTeam(teamId));
    }

    @GetMapping("/teams/{teamId}/players")
    public List<PlayerResponse> players(@PathVariable long teamId) {
        teamService.getActiveTeam(teamId);
        return playerService.findActiveByTeam(teamId).stream().map(PlayerResponse::from).toList();
    }

    @GetMapping("/players/{playerId}")
    public PlayerResponse player(@PathVariable long playerId) {
        return PlayerResponse.from(playerService.getActivePlayer(playerId));
    }

    public record TeamResponse(long id, String name, String shortName, String logoUrl,
                               String coachName, String homeVenue, TeamStatus status) {
        static TeamResponse from(Team team) {
            return new TeamResponse(team.getId(), team.getName(), team.getShortName(), team.getLogoUrl(),
                    team.getCoachName(), team.getHomeVenue(), team.getStatus());
        }
    }

    public record PlayerResponse(long id, long teamId, String name, String jerseyNo, String position,
                                 LocalDate birthDate, String photoUrl, PlayerStatus status) {
        static PlayerResponse from(Player player) {
            return new PlayerResponse(player.getId(), player.getTeamId(), player.getName(), player.getJerseyNo(),
                    player.getPosition(), player.getBirthDate(), player.getPhotoUrl(), player.getStatus());
        }
    }
}
