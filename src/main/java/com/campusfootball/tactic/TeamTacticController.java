package com.campusfootball.tactic;

import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manage")
public class TeamTacticController {
    private final TeamTacticService teamTacticService;
    private final CurrentUser currentUser;

    public TeamTacticController(TeamTacticService teamTacticService, CurrentUser currentUser) {
        this.teamTacticService = teamTacticService;
        this.currentUser = currentUser;
    }

    @GetMapping("/teams/{teamId}/tactics")
    public List<TeamTacticService.TacticResponse> list(@PathVariable long teamId) {
        return teamTacticService.findByTeam(currentUser.require().userId(), teamId);
    }

    @PostMapping("/teams/{teamId}/tactics")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamTacticService.TacticResponse create(@PathVariable long teamId,
                                                    @Valid @RequestBody SaveTacticRequest request) {
        return teamTacticService.create(currentUser.require().userId(), teamId, request.name(), request.positions());
    }

    @PutMapping("/tactics/{tacticId}")
    public TeamTacticService.TacticResponse update(@PathVariable long tacticId,
                                                    @Valid @RequestBody SaveTacticRequest request) {
        return teamTacticService.update(currentUser.require().userId(), tacticId, request.name(), request.positions());
    }

    @DeleteMapping("/tactics/{tacticId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long tacticId) {
        teamTacticService.delete(currentUser.require().userId(), tacticId);
    }

    public record SaveTacticRequest(
            @NotBlank @Size(max = 80) String name,
            @Valid @NotNull @Size(max = 30) List<TeamTacticService.TacticPosition> positions) {
    }
}

