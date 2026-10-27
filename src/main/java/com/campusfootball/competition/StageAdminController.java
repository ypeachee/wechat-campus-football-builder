package com.campusfootball.competition;

import com.campusfootball.common.api.IdResponse;
import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class StageAdminController {
    private final StageService stageService;
    private final CurrentUser currentUser;

    public StageAdminController(StageService stageService, CurrentUser currentUser) {
        this.stageService = stageService;
        this.currentUser = currentUser;
    }

    @PostMapping("/stages")
    public IdResponse create(@Valid @RequestBody CreateStageRequest request) {
        long id = stageService.create(currentUser.require().userId(), request.seasonId(), request.name(),
                request.type(), request.sortOrder(), request.status());
        return new IdResponse(id);
    }

    @PutMapping("/stages/{id}")
    public void update(@PathVariable long id, @Valid @RequestBody UpdateStageRequest request) {
        stageService.update(currentUser.require().userId(), id, request.name(), request.type(),
                request.sortOrder(), request.status());
    }

    public record CreateStageRequest(@NotNull Long seasonId,
                                     @NotBlank @Size(max = 64) String name,
                                     @NotNull StageType type,
                                     @Min(0) Integer sortOrder,
                                     StageStatus status) {
    }

    public record UpdateStageRequest(@NotBlank @Size(max = 64) String name,
                                     @NotNull StageType type,
                                     @Min(0) Integer sortOrder,
                                     StageStatus status) {
    }
}
