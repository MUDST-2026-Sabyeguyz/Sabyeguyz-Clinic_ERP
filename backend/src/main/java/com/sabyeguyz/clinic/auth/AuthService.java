package com.sabyeguyz.clinic.auth;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; import org.springframework.stereotype.Service;
@Service public class AuthService { private final AppUserRepository repo; private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(); public AuthService(AppUserRepository repo){this.repo=repo;}
 public AppUser register(String username,String password,Role role){return register(username,null,password,role);}
 public AppUser register(String username,String email,String password,Role role){if(repo.findByUsername(username).isPresent()||(email!=null&&!email.isBlank()&&repo.findByEmail(email).isPresent())) throw new IllegalArgumentException("ชื่อผู้ใช้หรืออีเมลนี้มีอยู่แล้ว"); if(password==null||password.length()<8) throw new IllegalArgumentException("รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร"); return repo.save(new AppUser(username,email,encoder.encode(password),role));}
 public AppUser login(String identifier,String password){AppUser user=repo.findByUsername(identifier).orElseGet(()->repo.findByEmail(identifier).orElseThrow(()->new IllegalArgumentException("ชื่อผู้ใช้ อีเมล หรือรหัสผ่านไม่ถูกต้อง"))); if(!user.isActive()||!encoder.matches(password,user.getPasswordHash())) throw new IllegalArgumentException("ชื่อผู้ใช้ อีเมล หรือรหัสผ่านไม่ถูกต้อง"); return user;}
}
