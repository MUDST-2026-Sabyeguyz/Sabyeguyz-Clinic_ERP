package com.sabyeguyz.clinicv2.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.staff.Staff;
import org.junit.jupiter.api.Test;

class CourseUsagePrintServiceTest {
    @Test
    void issuedDocumentKeepsPatientTherapistAndQuantityForReprints() throws Exception {
        var prints = mock(CourseUsagePrintRepository.class);
        var events = mock(CourseUsagePrintEventRepository.class);
        var usages = mock(CourseVisitUsageRepository.class);
        var service = new CourseUsagePrintService(prints, events, usages);

        var course = mock(Course.class);
        when(course.getId()).thenReturn(10L);
        var patient = mock(Patient.class);
        when(patient.getPatientFname()).thenReturn("สมชาย");
        when(patient.getPatientLname()).thenReturn("ใจดี");
        var therapist = mock(Staff.class);
        when(therapist.getStaffFname()).thenReturn("วิภา");
        when(therapist.getStaffLname()).thenReturn("รักษา");

        var usage = new CourseVisitUsage();
        usage.setCourse(course);
        usage.setPatient(patient);
        usage.setTreatingStaff(therapist);
        usage.setVisitDate(LocalDateTime.of(2026, 11, 13, 10, 0));
        usage.setVisitQty(2);
        usage.setUsageSequence(1);
        when(usages.findByCourseIdOrderByIdDesc(10L)).thenReturn(List.of(usage));
        when(prints.save(any(CourseUsagePrint.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var issued = service.issue(usage, 7L);
        var history = new ObjectMapper().readTree(issued.getDocumentSnapshot()).path("history").get(0);

        assertEquals(2, history.path("quantity").asInt());
        assertEquals("สมชาย ใจดี", history.path("patientName").asText());
        assertEquals("วิภา รักษา", history.path("therapistName").asText());
    }
}
