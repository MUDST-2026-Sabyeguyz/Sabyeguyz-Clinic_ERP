package com.sabyeguyz.clinicv2.auth;

import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** สร้าง JWT สำหรับบัญชี v2 พร้อม role และ authVersion ปัจจุบัน */
@Service
public class JwtTokenService {
    private final JwtEncoder encoder;

    public JwtTokenService(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String issue(
            AppUser user,
            List<String> roleNames) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("auth_version", user.getAuthVersion())
                .claim("roles", roleNames)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600));

        // ผู้ใช้ที่สมัครใหม่อาจยังไม่มี role จึงไม่ใส่ claim role เป็น null
        if (!roleNames.isEmpty()) {
            claims.claim("role", roleNames.get(0));
        }

        return encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims.build()))
                .getTokenValue();
    }
}
