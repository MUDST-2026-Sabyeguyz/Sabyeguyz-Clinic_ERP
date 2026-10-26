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

    public AuthController(AppUserService appUserService, UserRoleRepository userRoles, JwtTokenService tokenService) {
        this.appUserService = appUserService;
        this.userRoles = userRoles;
        this.tokenService = tokenService;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RegisterRequest(@NotBlank String username,@NotBlank @Size(max = 254) String email, @NotBlank String password) {}
    public record AuthResponse(Long id, String username, String email, String role, String token, String branchCode, String branchName) {}

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        AppUser user = appUserService.login(request.username(), request.password());
        appUserService.recordLogin(user.getId());
        return respond(user);
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
                branch == null ? null : branch.getBranchCode(), branch == null ? null : branch.getBranchName());
    }
}
