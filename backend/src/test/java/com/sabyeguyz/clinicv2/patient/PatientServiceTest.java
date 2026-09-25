package com.sabyeguyz.clinicv2.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.CustomerType;
import com.sabyeguyz.clinicv2.enums.Gender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {
    @Mock PatientRepository patients;
    @Mock HnGeneratorService hnGenerator;

    private PatientRegistration validRegistration(Branch branch) {
        return new PatientRegistration(branch, CustomerType.Thai, "นาย", "สมชาย", "ใจดี", Gender.Male,
                "0812345678", null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void registersAPatientAndAssignsAnHn() {
        Branch branch = new Branch();
        when(patients.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(hnGenerator.next(eq(branch), any())).thenReturn("2601120001");

        Patient patient = new PatientService(patients, hnGenerator).register(validRegistration(branch), 1L);

        assertThat(patient.getPatientHn()).isEqualTo("2601120001");
        assertThat(patient.getPatientFname()).isEqualTo("สมชาย");
        assertThat(patient.getRegisteredBy()).isEqualTo(1L);
    }

    @Test
    void rejectsMissingBranch() {
        assertThatThrownBy(() -> new PatientService(patients, hnGenerator).register(
                validRegistration(null), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingRequiredField() {
        Branch branch = new Branch();
        PatientRegistration missingPhone = new PatientRegistration(branch, CustomerType.Thai, "นาย", "สมชาย",
                "ใจดี", Gender.Male, "  ", null, null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> new PatientService(patients, hnGenerator).register(missingPhone, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
