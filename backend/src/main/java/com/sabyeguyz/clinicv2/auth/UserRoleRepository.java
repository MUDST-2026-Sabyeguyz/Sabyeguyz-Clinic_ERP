package com.sabyeguyz.clinicv2.auth;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    List<UserRole> findByUserId(Long userId);
}
