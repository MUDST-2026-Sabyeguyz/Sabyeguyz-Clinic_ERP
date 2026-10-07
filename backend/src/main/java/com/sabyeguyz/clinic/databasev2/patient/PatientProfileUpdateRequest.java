package com.sabyeguyz.clinic.databasev2.patient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/** Editable profile fields; patient identity, HN, type and branch remain server-owned. */
public class PatientProfileUpdateRequest {
    @NotBlank public String title;
    @NotBlank public String firstName;
    @NotBlank public String lastName;
    @NotBlank public String gender;
    public String phone;
    public String identityDocumentType;
    public String identityNumber;
    public String address;
    public String nickname;
    public String bloodGroup;
    public String nationality;
    public String customerType;
    public String customerSource;
    public String insuranceCompany;
    private LocalDate birthDate;
    private boolean birthDateProvided;

    @JsonSetter("birthDate")
    public void setBirthDate(LocalDate value) {
        birthDate = value;
        birthDateProvided = true;
    }

    public LocalDate getBirthDate() { return birthDate; }
    @JsonIgnore public boolean isBirthDateProvided() { return birthDateProvided; }

    public DatabaseV2PatientController.RegisterRequest values() {
        return new DatabaseV2PatientController.RegisterRequest(
                null, null, null, null, title, null, firstName, lastName, birthDate,
                gender, phone, identityDocumentType, identityNumber, address,
                null, null, null, null, null, null, null, nickname, bloodGroup,
                nationality, customerType, customerSource, insuranceCompany, null);
    }
}
