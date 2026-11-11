package com.sabyeguyz.clinicv2.patient;

import java.time.LocalDate;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.BloodGroup;
import com.sabyeguyz.clinicv2.enums.CustomerType;
import com.sabyeguyz.clinicv2.enums.Gender;
import com.sabyeguyz.clinicv2.masterdata.MasterDataValue;

/** ข้อมูลสำหรับสร้างโปรไฟล์ผู้ป่วย; ช่องที่ไม่บังคับยังเป็น null ได้ */
public record PatientRegistration(
        Branch branch,
        CustomerType customerType,
        /** คำนำหน้าที่ระบบรองรับ */
        String title,
        String patientFname,
        String patientLname,
        Gender gender,
        String phone,
        String genderDetail,
        String patientNickname,
        String nationalId,
        String passportNo,
        LocalDate birthdate,
        BloodGroup bloodGroup,
        String nationality,
        String address,
        MasterDataValue customerGroup,
        MasterDataValue referralChannel,
        MasterDataValue insuranceType,
        MasterDataValue insuranceCompany,
        String middleName,
        boolean drugAllergy,
        boolean foodAllergy,
        String chronicDisease,
        String emergencyContactName,
        String emergencyContactRelationship,
        String emergencyContactPhone,
        boolean privacyConsent) {

    /** รองรับ caller เดิมที่ยังไม่ส่งข้อมูลสุขภาพและผู้ติดต่อฉุกเฉิน */
    public PatientRegistration(
            Branch branch,
            CustomerType customerType,
            String title,
            String patientFname,
            String patientLname,
            Gender gender,
            String phone,
            String genderDetail,
            String patientNickname,
            String nationalId,
            String passportNo,
            LocalDate birthdate,
            BloodGroup bloodGroup,
            String nationality,
            String address,
            MasterDataValue customerGroup,
            MasterDataValue referralChannel,
            MasterDataValue insuranceType,
            MasterDataValue insuranceCompany,
            String middleName) {
        this(
                branch,
                customerType,
                title,
                patientFname,
                patientLname,
                gender,
                phone,
                genderDetail,
                patientNickname,
                nationalId,
                passportNo,
                birthdate,
                bloodGroup,
                nationality,
                address,
                customerGroup,
                referralChannel,
                insuranceType,
                insuranceCompany,
                middleName,
                false,
                false,
                null,
                null,
                null,
                null,
                false);
    }
}
