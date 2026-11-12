package com.sabyeguyz.clinicv2.role;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

<<<<<<< HEAD
public interface UserPermissionRepository
        extends JpaRepository<UserPermission, UserPermissionId> {

    Optional<UserPermission> findByUserIdAndPermissionId(
            Long userId, Long permissionId);

    List<UserPermission> findByUserId(Long userId);
}
=======
/** ค้นหา override ตามผู้ใช้และ permission */
public interface UserPermissionRepository extends JpaRepository<UserPermission, UserPermissionId> {
    Optional<UserPermission> findByUserIdAndPermissionId(Long userId, Long permissionId);
    List<UserPermission> findByUserId(Long userId);
}
>>>>>>> 3c7da8a915aea6793ee49626494196b33cc60e17
