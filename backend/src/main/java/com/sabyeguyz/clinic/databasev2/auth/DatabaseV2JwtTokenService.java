package com.sabyeguyz.clinic.databasev2.auth;

import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
@Profile("database-v2")
public class DatabaseV2JwtTokenService {
    private final JwtEncoder encoder;

    public DatabaseV2JwtTokenService(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String issue(DatabaseV2AppUser user) {
        Instant now = Instant.now();
        String role = user.primaryRole();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", role)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
