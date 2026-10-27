package com.campusfootball.auth;

import com.campusfootball.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @PostMapping("/auth/wechat-login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.code());
    }

    @GetMapping("/me")
    public MeResponse me() {
        return authService.me(currentUser.require());
    }

    public record LoginRequest(@NotBlank String code) {
    }
}
