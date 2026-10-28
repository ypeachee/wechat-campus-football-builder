package com.campusfootball.user;

import com.campusfootball.common.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserManagementController {
    private final UserManagementService userManagementService;
    private final CurrentUser currentUser;

    public UserManagementController(UserManagementService userManagementService, CurrentUser currentUser) {
        this.userManagementService = userManagementService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<UserManagementService.UserSearchResponse> search(
            @RequestParam(defaultValue = "") String query) {
        return userManagementService.search(currentUser.require().userId(), query);
    }
}