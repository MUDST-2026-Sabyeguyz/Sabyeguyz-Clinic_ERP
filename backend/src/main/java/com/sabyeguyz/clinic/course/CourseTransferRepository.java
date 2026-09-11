package com.sabyeguyz.clinic.course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface CourseTransferRepository extends JpaRepository<CourseTransfer, Long> {
    @Query("select t from CourseTransfer t where (:patientId is null or t.source.patient.id = :patientId or t.destination.patient.id = :patientId) order by t.id desc")
    List<CourseTransfer> history(@Param("patientId") Long patientId);
}
