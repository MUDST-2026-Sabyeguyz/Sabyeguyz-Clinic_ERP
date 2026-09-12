package com.sabyeguyz.clinic.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CourseRepositoryTest {
    @Test
    void courseKeepsConfiguredPackageValues() {
        Course course = new Course("P10", "Package", new BigDecimal("2500"), 10, 1, 90);

        assertEquals(10, course.getSessions());
        assertEquals(1, course.getBonusSessions());
        assertEquals(new BigDecimal("2500"), course.getPrice());
    }
}
