package com.campusfootball.dashboard;

import com.campusfootball.common.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/manage/dashboard")
public class ManagementDashboardController {
    private final ManagementDashboardService service;
    private final CurrentUser currentUser;

    public ManagementDashboardController(ManagementDashboardService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ManagementDashboardService.DashboardView get() {
        return service.get(currentUser.require().userId());
    }
}