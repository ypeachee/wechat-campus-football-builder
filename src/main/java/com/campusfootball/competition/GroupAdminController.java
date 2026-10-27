package com.campusfootball.competition;

import com.campusfootball.common.api.IdResponse;
import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class GroupAdminController {
    private final GroupService groupService;
    private final CurrentUser currentUser;

    public GroupAdminController(GroupService groupService, CurrentUser currentUser) {
        this.groupService = groupService;
        this.currentUser = currentUser;
    }

    @PostMapping("/stages/{stageId}/groups")
    public IdResponse create(@PathVariable long stageId, @Valid @RequestBody CreateGroupRequest request) {
        long id = groupService.create(currentUser.require().userId(), stageId, request.name(), request.sortOrder());
        return new IdResponse(id);
    }

    @PostMapping("/groups/{groupId}/teams")
    public void addTeam(@PathVariable long groupId, @Valid @RequestBody AddGroupTeamRequest request) {
        groupService.addTeam(currentUser.require().userId(), groupId, request.teamId(), request.seedNo());
    }

    @DeleteMapping("/groups/{groupId}/teams/{teamId}")
    public void removeTeam(@PathVariable long groupId, @PathVariable long teamId) {
        groupService.removeTeam(currentUser.require().userId(), groupId, teamId);
    }

    public record CreateGroupRequest(@NotBlank @Size(max = 32) String name,
                                     @Min(0) Integer sortOrder) {
    }

    public record AddGroupTeamRequest(@NotNull Long teamId, @Min(1) Integer seedNo) {
    }
}
