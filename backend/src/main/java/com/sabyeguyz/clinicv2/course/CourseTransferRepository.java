package com.sabyeguyz.clinicv2.course;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseTransferRepository extends JpaRepository<CourseTransfer, Long> {
    List<CourseTransfer> findByFromPatientIdOrToPatientIdOrderByIdDesc(Long fromPatientId, Long toPatientId);

    List<CourseTransfer> findAllByOrderByIdDesc();
}
