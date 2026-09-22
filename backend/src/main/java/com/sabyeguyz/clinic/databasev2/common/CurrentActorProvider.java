package com.sabyeguyz.clinic.databasev2.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentActorProvider {

    public Long getCurrentActorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return getActorId(auth);
    }

    public Long getActorId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String sub = jwt.getToken().getSubject();
            if (sub != null && !sub.isBlank()) {
                try {
                    return Long.valueOf(sub.trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (authentication != null && authentication.getName() != null) {
            try {
                return Long.valueOf(authentication.getName().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        throw new IllegalStateException("ไม่พบ actor ID จาก authentication context");
    }

    public Long getActorIdOrDefault(Authentication authentication, Long defaultId) {
        try {
            return getActorId(authentication);
        } catch (Exception e) {
            return defaultId;
        }
    }
}
