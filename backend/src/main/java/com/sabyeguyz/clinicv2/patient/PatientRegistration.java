package com.sabyeguyz.clinicv2.patient;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.BloodGroup;
import com.sabyeguyz.clinicv2.enums.CustomerType;
import com.sabyeguyz.clinicv2.enums.Gender;
import com.sabyeguyz.clinicv2.masterdata.MasterDataValue;
import java.time.LocalDate;

/**
 * Input for {@link PatientService#register}. Fields marked required below
 * follow the Clinic ERP Workflow Brief §3 "PatientProfile Required Information"
 * table; everything else is optional.
 */
public record PatientRegistration(
        Branch branch,
        CustomerType customerType,
        /** Canonical นาย, นางสาว, นาง, เด็กชาย, เด็กหญิง; the HTTP contract also accepts English aliases. */
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
        String middleName
) {
}
