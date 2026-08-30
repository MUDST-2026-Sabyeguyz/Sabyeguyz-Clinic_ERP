package com.sabyeguyz.clinic.patient;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PatientRegistrationServiceTest {
    @Autowired PatientRegistrationService registrationService;
    @Autowired EntityManager entityManager;

    @Test
    void persistsPatientAndGeneratesBranchHn() {
        entityManager.createNativeQuery("UPDATE branches SET numeric_code = '07' WHERE code = 'RP'").executeUpdate();
        PatientResponse response = registrationService.register(new PatientRegistrationRequest(
                "RP", "prototype", "prototype@example.com", "password123", "นาย", "ผู้ป่วย ทดสอบ",
                LocalDate.of(1990, 1, 1), "ชาย", "0812345678", "NATIONAL_ID", "1100000000001",
                "กรุงเทพฯ", false, false, "", "ผู้ติดต่อ ทดสอบ", "ญาติ", "0899999999", true));

        assertThat(response.hn()).matches("\\d{2}07\\d{2}\\d{4}");
        assertThat(response.active()).isTrue();
    }

    @Test
    void incrementsRunningNumberForSameBranchAndMonth() {
        entityManager.createNativeQuery("UPDATE branches SET numeric_code = '07' WHERE code = 'RP'").executeUpdate();
        PatientRegistrationRequest first = request("running-one", "running-one@example.com", "1100000000002");
        PatientRegistrationRequest second = request("running-two", "running-two@example.com", "1100000000003");

        String firstHn = registrationService.register(first).hn();
        String secondHn = registrationService.register(second).hn();

        assertThat(firstHn).endsWith("0001");
        assertThat(secondHn).endsWith("0002");
        assertThat(firstHn).hasSize(10).isNotEqualTo(secondHn);
    }

    private PatientRegistrationRequest request(String username, String email, String identityNumber) {
        return new PatientRegistrationRequest("RP", username, email, "password123", "นาย", "ผู้ป่วย " + username,
                LocalDate.of(1990, 1, 1), "ชาย", "0812345678", "NATIONAL_ID", identityNumber,
                "กรุงเทพฯ", false, false, "", "ผู้ติดต่อ", "ญาติ", "0899999999", true);
    }
}
