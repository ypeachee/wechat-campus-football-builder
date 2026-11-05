package com.campusfootball.user;

import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class WebCredentialController {
    private final WebCredentialService webCredentialService;
    private final CurrentUser currentUser;

    public WebCredentialController(WebCredentialService webCredentialService, CurrentUser currentUser) {
        this.webCredentialService = webCredentialService;
        this.currentUser = currentUser;
    }

    @PutMapping("/{userId}/web-credentials")
    public WebCredentialService.WebCredentialResponse setCredentials(
            @PathVariable long userId,
            @Valid @RequestBody WebCredentialRequest request) {
        return webCredentialService.setCredentials(
                currentUser.require().userId(), userId, request.username(), request.password());
    }

    public record WebCredentialRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }
}
