package com.sabyeguyz.clinicv2.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolePermissionRepository
        extends JpaRepository<RolePermission, RolePermissionId> {

    @Query("""
            select count(rp)
            from RolePermission rp
            where rp.permission.id = :permissionId
              and rp.role.id in (
                  select ur.role.id
                  from UserRole ur
                  where ur.user.id = :userId
              )
            """)
    long countGrantedToUser(
            @Param("userId") Long userId,
            @Param("permissionId") Long permissionId);
}
