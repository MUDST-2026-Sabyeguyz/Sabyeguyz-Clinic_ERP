package com.sabyeguyz.clinicv2.idcard;

import java.time.LocalDate;

/** What the agent read from the chip (no photo). Dates are Gregorian; a lifetime card has no expiry date. */
public record CardData(
        String nationalId,
        String titleTh,
        String firstNameTh,
        String middleNameTh,
        String lastNameTh,
        String fullNameEn,
        LocalDate birthDate,
        String gender,
        String address,
        LocalDate issueDate,
        LocalDate expiryDate,
        boolean lifetime) {}
