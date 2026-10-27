package com.campusfootball.competition;

import com.campusfootball.common.api.AppException;
import com.campusfootball.common.api.ErrorCode;
import com.campusfootball.common.api.IdResponse;
import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class EventManagementController {
    private final EventService eventService;
    private final CurrentUser currentUser;

    public EventManagementController(EventService eventService, CurrentUser currentUser) {
        this.eventService = eventService;
        this.currentUser = currentUser;
    }

    @PostMapping("/fixtures/{fixtureId}/events")
    public IdResponse create(@PathVariable long fixtureId, @Valid @RequestBody CreateEventRequest request) {
        long id = eventService.create(currentUser.require().userId(), fixtureId, request.teamId(),
                request.playerId(), request.eventType(), request.relatedPlayerId(), request.minute(),
                request.stoppageMinute(), request.note());
        return new IdResponse(id);
    }

    @PutMapping("/events/{eventId}")
    public void update(@PathVariable long eventId, @Valid @RequestBody UpdateEventRequest request) {
        if (request.teamId() != null) {
            throw new AppException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                    "事件归属球队不可修改，请删除后重建");
        }
        eventService.update(currentUser.require().userId(), eventId, request.playerId(), request.eventType(),
                request.relatedPlayerId(), request.minute(), request.stoppageMinute(), request.note());
    }

    @DeleteMapping("/events/{eventId}")
    public void delete(@PathVariable long eventId) {
        eventService.delete(currentUser.require().userId(), eventId);
    }

    public record CreateEventRequest(@NotNull Long teamId,
                                     @NotNull Long playerId,
                                     @NotNull EventType eventType,
                                     Long relatedPlayerId,
                                     @NotNull @Min(1) @Max(130) Integer minute,
                                     @NotNull @Min(0) @Max(30) Integer stoppageMinute,
                                     @Size(max = 255) String note) {
    }

    public record UpdateEventRequest(@NotNull Long playerId,
                                     @NotNull EventType eventType,
                                     Long relatedPlayerId,
                                     @NotNull @Min(1) @Max(130) Integer minute,
                                     @NotNull @Min(0) @Max(30) Integer stoppageMinute,
                                     @Size(max = 255) String note,
                                     Long teamId) {
    }
}
