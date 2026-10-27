package com.campusfootball.competition;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class GroupController {
    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/groups")
    public List<GroupResponse> list(@RequestParam long stageId) {
        return groupService.findByStage(stageId).stream().map(GroupResponse::from).toList();
    }

    @GetMapping("/groups/{groupId}/teams")
    public List<GroupTeamResponse> teams(@PathVariable long groupId) {
        return groupService.findTeams(groupId).stream().map(GroupTeamResponse::from).toList();
    }

    public record GroupResponse(long id, long stageId, String name, int sortOrder) {
        static GroupResponse from(CompetitionGroup group) {
            return new GroupResponse(group.getId(), group.getStageId(), group.getName(), group.getSortOrder());
        }
    }

    public record GroupTeamResponse(long id, long groupId, long teamId, Integer seedNo) {
        static GroupTeamResponse from(GroupTeam groupTeam) {
            return new GroupTeamResponse(groupTeam.getId(), groupTeam.getGroupId(), groupTeam.getTeamId(),
                    groupTeam.getSeedNo());
        }
    }
}
