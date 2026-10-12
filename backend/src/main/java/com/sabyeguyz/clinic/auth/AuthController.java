package com.sabyeguyz.clinic.auth;
import com.sabyeguyz.clinic.staff.StaffRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequestMapping("/api/auth") public class AuthController { private final AuthService service; private final JwtTokenService tokens; private final StaffRepository staff; public AuthController(AuthService service, JwtTokenService tokens, StaffRepository staff){this.service=service;this.tokens=tokens;this.staff=staff;}
 record Request(@NotBlank String username,String email,@NotBlank String password,Role role){} record Response(Long id,String username,String email,Role role,String branchCode,String branchName,String token){}
 @PostMapping("/register") @PreAuthorize("hasRole('MANAGER')") Response register(@RequestBody @jakarta.validation.Valid Request r){AppUser u=service.register(r.username(),r.email(),r.password(),r.role()==null?Role.MANAGER:r.role());return response(u);}
 @PostMapping("/login") @Transactional Response login(@RequestBody @jakarta.validation.Valid Request r){return response(service.login(r.username(),r.password()));}
 private Response response(AppUser user){var branch=staff.findById(user.getId()).map(value->value.getBranch()).orElse(null);return new Response(user.getId(),user.getUsername(),user.getEmail(),user.getRole(),branch==null?null:branch.getBranchCode(),branch==null?null:branch.getName(),tokens.issue(user));}
}
