package com.sabyeguyz.clinicv2.idcard;

/** Check digit of a Thai national ID number: 13 digits, weights 13..2, mod 11. */
public final class ThaiNationalId {
    private ThaiNationalId() {}

    public static boolean isValid(String value) {
        if (value == null || !value.matches("[0-9]{13}")) return false;

        int sum = 0;

        for (int i = 0; i < 12; i++) sum += (value.charAt(i) - '0') * (13 - i);

        return (11 - sum % 11) % 10 == value.charAt(12) - '0';
    }
}
