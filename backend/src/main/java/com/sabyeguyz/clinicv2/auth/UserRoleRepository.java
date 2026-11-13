package com.sabyeguyz.clinicv2.auth;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository
        extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUserId(Long userId);

    @Query("""
            select ur.role.roleName
            from UserRole ur
            where ur.user.id = :userId
            """)
    List<String> findRoleNamesByUserId(
            @Param("userId") Long userId);

    void deleteByUserId(Long userId);
}