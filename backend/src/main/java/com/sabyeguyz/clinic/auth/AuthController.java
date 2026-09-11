package com.sabyeguyz.clinic.auth;
import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth") public class AuthController { private final AuthService service; private final JwtTokenService tokens; public AuthController(AuthService service, JwtTokenService tokens){this.service=service;this.tokens=tokens;}
 record Request(@NotBlank String username,@NotBlank String password,Role role){} record Response(Long id,String username,Role role,String token){}
 @PostMapping("/register") Response register(@RequestBody @jakarta.validation.Valid Request r){AppUser u=service.register(r.username(),r.password(),r.role()==null?Role.MANAGER:r.role());return new Response(u.getId(),u.getUsername(),u.getRole(),tokens.issue(u));}
 @PostMapping("/login") Response login(@RequestBody @jakarta.validation.Valid Request r){AppUser u=service.login(r.username(),r.password());return new Response(u.getId(),u.getUsername(),u.getRole(),tokens.issue(u));}
}
