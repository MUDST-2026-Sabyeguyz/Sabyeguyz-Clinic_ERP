package com.sabyeguyz.idcard.agent;

import java.time.LocalDate;

/** What is read from the chip (no photo). Same shape the backend accepts. */
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
