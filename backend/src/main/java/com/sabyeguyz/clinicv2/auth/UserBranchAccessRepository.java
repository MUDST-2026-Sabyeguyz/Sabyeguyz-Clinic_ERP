package com.sabyeguyz.clinicv2.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBranchAccessRepository extends JpaRepository<UserBranchAccess, UserBranchAccessId> {
}
