package com.sabyeguyz.clinicv2.auth;

import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    Optional<AppUser> findByEmailIgnoreCase(String email);
    List<AppUser> findByStaffId(Long staffId);

    @Modifying
    @Query("update AppUser u set u.lastLoginAt = :at where u.id = :id")
    int recordLogin(@Param("id") Long id, @Param("at") java.time.Instant at);

    /** The staff whose user account has been closed (ปิดใช้งาน): they are not offered anywhere else. */
    default Set<Long> closedStaffIds() {
        return findAll().stream()
                .filter(user -> !user.isUserStatus() && user.getStaff() != null)
                .map(user -> user.getStaff().getId())
                .collect(Collectors.toSet());
    }
}
