package com.sabyeguyz.clinic.databasev2.auth;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Profile("database-v2")
public class DatabaseV2AuthService {
    private final DatabaseV2AppUserRepository users;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public DatabaseV2AuthService(DatabaseV2AppUserRepository users) {
        this.users = users;
    }

    @Transactional
    public DatabaseV2AppUser login(String username, String password) {
        DatabaseV2AppUser user = users.findByUsername(username.trim())
                .orElseThrow(DatabaseV2AuthService::invalidCredentials);
        if (!user.isActive() || !encoder.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        users.recordLogin(user.id(), LocalDateTime.now());
        return user;
    }

    private static org.springframework.security.authentication.BadCredentialsException invalidCredentials() {
        return new org.springframework.security.authentication.BadCredentialsException("ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง");
    }
}
