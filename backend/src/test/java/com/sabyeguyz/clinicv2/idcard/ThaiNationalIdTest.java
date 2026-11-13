package com.sabyeguyz.clinicv2.idcard;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ThaiNationalIdTest {
    @Test
    void acceptsANumberWithAMatchingCheckDigit() {
        // 1*13+1*12+0*11+1*10+7*9+0*8+0*7+0*6+0*5+5*4+1*3+1*2 = 123; 123 % 11 = 2; (11 - 2) % 10 = 9
        assertTrue(ThaiNationalId.isValid("1101700005119"));
    }

    @Test
    void rejectsAWrongCheckDigit() {
        assertFalse(ThaiNationalId.isValid("1101700005110"));
    }

    @Test
    void rejectsNullShortLongAndNonDigits() {
        assertFalse(ThaiNationalId.isValid(null));
        assertFalse(ThaiNationalId.isValid("123"));
        assertFalse(ThaiNationalId.isValid("11017000051190"));
        assertFalse(ThaiNationalId.isValid("110170000511A"));
        assertFalse(ThaiNationalId.isValid("0000000000000"));
    }
}
