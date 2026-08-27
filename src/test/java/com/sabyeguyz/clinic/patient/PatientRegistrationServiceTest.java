package com.sabyeguyz.clinic.patient;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PatientRegistrationServiceTest {
    @Autowired PatientRegistrationService registrationService;

    @Test
    void persistsPatientAndGeneratesBranchHn() {
        PatientResponse response = registrationService.register(new PatientRegistrationRequest(
                "RP", "prototype", "prototype@example.com", "password123", "นาย", "ผู้ป่วย ทดสอบ",
                LocalDate.of(1990, 1, 1), "ชาย", "0812345678", "NATIONAL_ID", "1100000000001",
                "กรุงเทพฯ", false, false, "", "ผู้ติดต่อ ทดสอบ", "ญาติ", "0899999999", true));

        assertThat(response.hn()).matches("RP-\\d{6}");
        assertThat(response.active()).isTrue();
    }
}
