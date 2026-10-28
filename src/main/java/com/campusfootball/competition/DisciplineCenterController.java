package com.campusfootball.competition;

import com.campusfootball.common.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/manage/discipline")
public class DisciplineCenterController {
    private final DisciplineCenterService disciplineCenterService;
    private final CurrentUser currentUser;

    public DisciplineCenterController(DisciplineCenterService disciplineCenterService, CurrentUser currentUser) {
        this.disciplineCenterService = disciplineCenterService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public DisciplineCenterService.DisciplineCenterView get(@RequestParam long seasonId,
                                                            @RequestParam(required = false) Long teamId) {
        return disciplineCenterService.get(currentUser.require().userId(), seasonId, teamId);
    }
}