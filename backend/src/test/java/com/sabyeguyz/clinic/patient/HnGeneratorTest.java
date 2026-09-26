package com.sabyeguyz.clinic.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class HnGeneratorTest {
    @Test
    void formatsYearBranchMonthAndRunningNumber() {
        assertThat(HnGenerator.format(LocalDate.of(2026, 12, 1), "01", 1)).isEqualTo("2601120001");
    }

    @Test
    void monthIsPartOfHnAndNaturallyResetsCounterScope() {
        assertThat(HnGenerator.format(LocalDate.of(2026, 11, 30), "02", 1)).isEqualTo("2602110001");
        assertThat(HnGenerator.format(LocalDate.of(2026, 12, 1), "02", 1)).isEqualTo("2602120001");
    }

    @Test
    void patientHnIsImmutableAfterFirstAssignment() {
        Patient patient = new Patient();
        patient.assignHn("2601120001");
        patient.assignHn("2602120001");
        assertThat(patient.getHn()).isEqualTo("2601120001");
    }

    @Test
    void rejectsUnresolvedOrNonNumericBranchCode() {
        assertThatThrownBy(() -> new HnGenerator(null).next(1L, "AB", LocalDate.of(2026, 12, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
