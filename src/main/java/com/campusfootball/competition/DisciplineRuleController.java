package com.campusfootball.competition;

import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/competitions/{competitionId}/discipline-rule")
public class DisciplineRuleController {
    private final DisciplineRuleService disciplineRuleService;
    private final CurrentUser currentUser;

    public DisciplineRuleController(DisciplineRuleService disciplineRuleService, CurrentUser currentUser) {
        this.disciplineRuleService = disciplineRuleService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public DisciplineRule get(@PathVariable long competitionId) {
        return disciplineRuleService.get(currentUser.require().userId(), competitionId);
    }

    @PutMapping
    public DisciplineRule update(@PathVariable long competitionId,
                                 @Valid @RequestBody UpdateDisciplineRuleRequest request) {
        return disciplineRuleService.update(currentUser.require().userId(), competitionId,
                request.yellowSuspensionThreshold(), request.redSuspensionMatches(), request.cardsCrossStages());
    }

    public record UpdateDisciplineRuleRequest(@NotNull @Min(1) @Max(9) Integer yellowSuspensionThreshold,
                                              @NotNull @Min(1) @Max(20) Integer redSuspensionMatches,
                                              @NotNull Boolean cardsCrossStages) {
    }
}