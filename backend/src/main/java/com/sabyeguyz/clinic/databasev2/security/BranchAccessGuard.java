package com.sabyeguyz.clinic.databasev2.security;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Uses the configured user-to-branch relationship, including for managers. */
@Component
@Profile("database-v2")
public class BranchAccessGuard {
    private final JdbcTemplate jdbc;
    public BranchAccessGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Long actorId(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) throw new NumberFormatException();
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException invalid) {
            throw new AccessDeniedException("ไม่มีสิทธิ์เข้าถึงข้อมูล");
        }
    }

    public java.util.List<Long> allowedBranchIds(Authentication authentication) {
        return jdbc.queryForList("select branch_id from user_branch_access where user_id = ?",
                Long.class, actorId(authentication));
    }

    public void require(Authentication authentication, Long branchId) {
        Long actorId = actorId(authentication);
        Integer count = jdbc.queryForObject(
                "select count(*) from user_branch_access where user_id = ? and branch_id = ?",
                Integer.class, actorId, branchId);
        if (count == null || count == 0) throw new AccessDeniedException("ไม่มีสิทธิ์เข้าถึงสาขานี้");
    }
}
