package com.sabyeguyz.clinicv2.course;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseSharedMemberRepository extends JpaRepository<CourseSharedMember, CourseSharedMemberId> {
}
