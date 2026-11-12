package com.sabyeguyz.clinicv2.auth;

import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Matches frontend/src/api/client.ts's authApi (POST /api/auth/login,
 * /api/auth/register). V2 supports multiple Roles per user (User_role)
 * where the old API had a single enum. The response returns the first assigned role's name
 * to keep the response shape
 * compatible, which loses information for a multi-role user.
 */
@Transactional
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppUserService appUserService;
    private final UserRoleRepository userRoles;
    private final JwtTokenService tokenService;
    private final LoginAttemptLimiter limiter;
    private final CurrentActor actor;

    public AuthController(AppUserService appUserService, UserRoleRepository userRoles, JwtTokenService tokenService,
                          LoginAttemptLimiter limiter, CurrentActor actor) {
        this.actor = actor;
        this.limiter = limiter;
        this.appUserService = appUserService;
        this.userRoles = userRoles;
        this.tokenService = tokenService;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RegisterRequest(@NotBlank String username,@NotBlank @Size(max = 254) String email, @NotBlank String password) {}
    public record AuthResponse(Long id, String username, String email, String role, String token,
                               String branchCode, String branchName, Long staffId) {}

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        if (limiter.isBlocked(request.username())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "พยายามเข้าสู่ระบบผิดหลายครั้ง กรุณารอสักครู่แล้วลองใหม่");
        }
        AppUser user;
        try {
            user = appUserService.login(request.username(), request.password());
        } catch (IllegalArgumentException failure) {
            if (AppUserService.INVALID_CREDENTIALS.equals(failure.getMessage())) {
                limiter.recordFailure(request.username());
                throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, failure.getMessage());
            }
            throw failure;
        }
        limiter.recordSuccess(request.username());
        appUserService.recordLogin(user.getId());
        return respond(user);
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(max = 100) String newPassword) {}

    /** The caller's other tokens stop working; the response carries a fresh one. */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public AuthResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return respond(appUserService.changeOwnPassword(actor.requireId(), request.currentPassword(), request.newPassword()));
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        AppUser user = appUserService.register(request.username(), request.email(), request.password(), null, null);
        return respond(user);
    }

    private AuthResponse respond(AppUser user) {
        List<String> roleNames = userRoles.findByUserId(user.getId()).stream()
                .map(userRole -> userRole.getRole().getRoleName().toUpperCase(java.util.Locale.ROOT))
                .toList();
        String token = tokenService.issue(user, roleNames);
        String primaryRole = roleNames.isEmpty() ? null : roleNames.get(0);
        var branch = user.getStaff() == null ? null : user.getStaff().getBranch();
        return new AuthResponse(user.getId(), user.getUsername(), user.getEmail(), primaryRole, token,
                branch == null ? null : branch.getBranchCode(), branch == null ? null : branch.getBranchName(),
                user.getStaff() == null ? null : user.getStaff().getId());
    }
}
