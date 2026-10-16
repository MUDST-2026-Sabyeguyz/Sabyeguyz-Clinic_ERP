package com.sabyeguyz.clinicv2.auth;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByEmailIgnoreCase(String email);

    /** The staff whose user account has been closed (ปิดใช้งาน): they are not offered anywhere else. */
    default Set<Long> closedStaffIds() {
        return findAll().stream()
                .filter(user -> !user.isUserStatus() && user.getStaff() != null)
                .map(user -> user.getStaff().getId())
                .collect(Collectors.toSet());
    }
}
