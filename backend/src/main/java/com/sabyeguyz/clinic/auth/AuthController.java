package com.sabyeguyz.clinic.auth;
import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/auth") public class AuthController { private final AuthService service; public AuthController(AuthService service){this.service=service;}
 record Request(@NotBlank String username,@NotBlank String password,Role role){} record Response(Long id,String username,Role role){}
 @PostMapping("/register") Response register(@RequestBody @jakarta.validation.Valid Request r){AppUser u=service.register(r.username(),r.password(),r.role()==null?Role.STAFF:r.role());return new Response(u.getId(),u.getUsername(),u.getRole());}
 @PostMapping("/login") Response login(@RequestBody @jakarta.validation.Valid Request r){AppUser u=service.login(r.username(),r.password());return new Response(u.getId(),u.getUsername(),u.getRole());}
}
