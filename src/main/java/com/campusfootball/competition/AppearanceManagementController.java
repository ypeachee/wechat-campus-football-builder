package com.campusfootball.competition;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.common.api.IdResponse;
import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/manage")
public class AppearanceManagementController {
    private final AppearanceService appearanceService;
    private final CurrentUser currentUser;

    public AppearanceManagementController(AppearanceService appearanceService, CurrentUser currentUser) {
        this.appearanceService = appearanceService;
        this.currentUser = currentUser;
    }

    @PostMapping("/fixtures/{fixtureId}/appearances")
    public IdResponse create(@PathVariable long fixtureId, @Valid @RequestBody CreateAppearanceRequest request) {
        long id = appearanceService.create(currentUser.require().userId(), fixtureId, request.teamId(),
                request.playerId(), request.isStarter(), request.minutesPlayed());
        return new IdResponse(id);
    }

    @PutMapping("/appearances/{appearanceId}")
    public void update(@PathVariable long appearanceId, @Valid @RequestBody UpdateAppearanceRequest request) {
        if (request.teamId() != null || request.playerId() != null) {
            throw new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                    "出场记录的球队和球员不可修改，请删除后重建");
        }
        appearanceService.update(currentUser.require().userId(), appearanceId,
                request.isStarter(), request.minutesPlayed());
    }

    @DeleteMapping("/appearances/{appearanceId}")
    public void delete(@PathVariable long appearanceId) {
        appearanceService.delete(currentUser.require().userId(), appearanceId);
    }

    public record CreateAppearanceRequest(@NotNull Long teamId,
                                          @NotNull Long playerId,
                                          @NotNull Boolean isStarter,
                                          @Min(0) @Max(130) Integer minutesPlayed) {
    }

    public record UpdateAppearanceRequest(@NotNull Boolean isStarter,
                                          @Min(0) @Max(130) Integer minutesPlayed,
                                          Long teamId,
                                          Long playerId) {
    }
}
