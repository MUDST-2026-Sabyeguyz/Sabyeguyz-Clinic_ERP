package com.sabyeguyz.clinicv2.auth;

import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Reuses the shared {@link JwtEncoder} bean from SecurityConfig (same
 * signing key as the v1 API) so both can coexist until cutover — the
 * subject is just the v2 app_user.user_id as a string.
 */
@Service
public class JwtTokenService {
    private final JwtEncoder encoder;

    public JwtTokenService(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String issue(AppUser user, List<String> roleNames) {
        Instant now = Instant.now();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .subject(String.valueOf(user.getId()))
                        .claim("username", user.getUsername())
                        .claim("role", roleNames.isEmpty() ? null : roleNames.get(0))
                        .claim("roles", roleNames)
                        .issuedAt(now).expiresAt(now.plusSeconds(3600)).build()))
                .getTokenValue();
    }
}
