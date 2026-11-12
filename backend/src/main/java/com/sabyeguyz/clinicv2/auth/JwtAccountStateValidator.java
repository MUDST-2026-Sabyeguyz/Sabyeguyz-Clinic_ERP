package com.sabyeguyz.clinicv2.auth;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** ปฏิเสธ token เมื่อบัญชีปิดหรือ authVersion ไม่ตรงกับฐานข้อมูล */
@Component
public class JwtAccountStateValidator {
    private final AppUserRepository users;

    public JwtAccountStateValidator(AppUserRepository users) {
        this.users = users;
    }

    public void requireCurrent(Jwt jwt) {
        // ตรวจบัญชีและเวอร์ชันปัจจุบันทุกครั้งที่ใช้ bearer token
        final long userId;

        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException | NullPointerException exception) {
            throw new BadCredentialsException("Invalid account subject", exception);
        }

        AppUser current = users.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Account no longer exists"));
        Number tokenVersion = jwt.getClaim("auth_version");
        if (!current.isUserStatus() || tokenVersion == null
                || tokenVersion.intValue() != current.getAuthVersion()) {
            throw new BadCredentialsException("Account session is no longer valid");
        }
    }
}
