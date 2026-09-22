package com.sabyeguyz.clinic.databasev2.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("database-v2")
@RequestMapping("/api/auth")
public class DatabaseV2AuthController {
    private final DatabaseV2AuthService auth;
    private final DatabaseV2JwtTokenService tokens;

    public DatabaseV2AuthController(DatabaseV2AuthService auth, DatabaseV2JwtTokenService tokens) {
        this.auth = auth;
        this.tokens = tokens;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(Long id, String username, String role, String token) {}

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        DatabaseV2AppUser user = auth.login(request.username(), request.password());
        return new LoginResponse(user.getId(), user.getUsername(), user.primaryRole(), tokens.issue(user));
    }
}
