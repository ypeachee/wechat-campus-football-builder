package com.campusfootball.competition;

import com.campusfootball.common.api.IdResponse;
import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class CompetitionAdminController {
    private final CompetitionService competitionService;
    private final CurrentUser currentUser;

    public CompetitionAdminController(CompetitionService competitionService, CurrentUser currentUser) {
        this.competitionService = competitionService;
        this.currentUser = currentUser;
    }

    @GetMapping("/competitions")
    public List<CompetitionController.CompetitionResponse> list() {
        return competitionService.findAll().stream().map(CompetitionController.CompetitionResponse::from).toList();
    }

    @PostMapping("/competitions")
    public IdResponse create(@Valid @RequestBody CreateCompetitionRequest request) {
        long id = competitionService.create(currentUser.require().userId(), request.name(), request.type(), request.description());
        return new IdResponse(id);
    }

    @PutMapping("/competitions/{id}")
    public void update(@PathVariable long id, @Valid @RequestBody UpdateCompetitionRequest request) {
        competitionService.update(currentUser.require().userId(), id, request.name(), request.type(),
                request.description(), request.status());
    }

    public record CreateCompetitionRequest(@NotBlank @Size(max = 100) String name,
                                           @NotNull CompetitionType type,
                                           @Size(max = 255) String description) {
    }

    public record UpdateCompetitionRequest(@NotBlank @Size(max = 100) String name,
                                           @NotNull CompetitionType type,
                                           @Size(max = 255) String description,
                                           CompetitionStatus status) {
    }
}
