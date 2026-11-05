package com.campusfootball.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class WebAuthController {
    private final WebAuthService webAuthService;

    public WebAuthController(WebAuthService webAuthService) {
        this.webAuthService = webAuthService;
    }

    @PostMapping("/web-login")
    public AuthResponse login(@Valid @RequestBody WebLoginRequest request) {
        return webAuthService.login(request.username(), request.password());
    }

    public record WebLoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 72) String password) {
    }
}
