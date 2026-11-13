package com.sabyeguyz.clinicv2.role;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** ค้นหา override ตามผู้ใช้และ permission */
public interface UserPermissionRepository extends JpaRepository<UserPermission, UserPermissionId> {
    Optional<UserPermission> findByUserIdAndPermissionId(
            Long userId,
            Long permissionId);
    List<UserPermission> findByUserId(Long userId);
}
