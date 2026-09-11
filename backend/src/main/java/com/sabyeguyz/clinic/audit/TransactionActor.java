package com.sabyeguyz.clinic.audit;

import com.sabyeguyz.clinic.auth.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class TransactionActor {
    private final AppUserRepository users;
    public TransactionActor(AppUserRepository users) { this.users = users; }

    public Long requireId() {
        Long id = currentId();
        if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        return id;
    }

    /** Null is reserved for legacy, non-HTTP service calls without an authenticated actor. */
    public Long currentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) return null;
        try {
            long id = Long.parseLong(authentication.getName());
            if (id <= 0 || users.findById(id).filter(user -> user.isActive()).isEmpty())
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Active user required");
            return id;
        } catch (NumberFormatException invalidSubject) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated subject");
        }
    }
}
