package com.sabyeguyz.clinicv2.patient;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.BloodGroup;
import com.sabyeguyz.clinicv2.enums.CustomerType;
import com.sabyeguyz.clinicv2.enums.Gender;
import com.sabyeguyz.clinicv2.masterdata.MasterDataValue;

/** ข้อมูลผู้ป่วยที่บันทึกในฐานข้อมูล รวมข้อมูลสุขภาพและผู้ติดต่อฉุกเฉิน */
@Entity
@Table(name = "patient")
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patient_id")
    private Long id;

    /** รูปแบบ HN: YYBBMMNNNN */
    @Column(name = "patient_hn", length = 10, unique = true)
    private String patientHn;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "customer_type", nullable = false, columnDefinition = "customer_type_enum")
    private CustomerType customerType;

    /** เก็บคำนำหน้าตาม PostgreSQL enum */
    @ColumnTransformer(write = "cast(? as title_enum)")
    @Column(length = 20, nullable = false)
    private String title;

    @Column(name = "patient_fname", length = 100, nullable = false)
    private String patientFname;

    @Column(name = "patient_lname", length = 100, nullable = false)
    private String patientLname;

    @Column(name = "patient_middle_name", length = 100)
    private String patientMiddleName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "gender_enum")
    private Gender gender;

    @Column(length = 20, nullable = false)
    private String phone;

    @Column(name = "email", length = 254)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "gender_detail", length = 50)
    private String genderDetail;

    @Column(name = "patient_nickname", length = 50)
    private String patientNickname;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "national_id", length = 13)
    private String nationalId;

    @Column(name = "passport_no", length = 20)
    private String passportNo;

    private LocalDate birthdate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "blood_group", columnDefinition = "blood_group_enum")
    private BloodGroup bloodGroup;

    @Column(length = 50)
    private String nationality;

    @Column(length = 255)
    private String address;

    // ข้อมูลสุขภาพและความยินยอมที่ต้องคงอยู่ในโปรไฟล์ผู้ป่วย
    @Column(name = "drug_allergy", nullable = false)
    private boolean drugAllergy;

    @Column(name = "food_allergy", nullable = false)
    private boolean foodAllergy;

    @Column(name = "chronic_disease", length = 500)
    private String chronicDisease;

    @Column(name = "emergency_contact_name", length = 160)
    private String emergencyContactName;

    @Column(name = "emergency_contact_relationship", length = 80)
    private String emergencyContactRelationship;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "privacy_consent", nullable = false)
    private boolean privacyConsent;

    @Column(name = "registered_by", nullable = false, updatable = false)
    private Long registeredBy;

    @Column(name = "patient_update_at")
    private Instant patientUpdateAt;

    @Column(name = "patient_update_by")
    private Long patientUpdateBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_group_value_id")
    private MasterDataValue customerGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referral_channel_value_id")
    private MasterDataValue referralChannel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurance_type_value_id")
    private MasterDataValue insuranceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurance_company_value_id")
    private MasterDataValue insuranceCompany;

    public Patient() {}

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

    public String getPatientHn() {
        return patientHn;
    }
    public void setPatientHn(String patientHn) {
        this.patientHn = patientHn;
    }

    public CustomerType getCustomerType() {
        return customerType;
    }
    public void setCustomerType(CustomerType customerType) {
        this.customerType = customerType;
    }

    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    public String getPatientFname() {
        return patientFname;
    }
    public void setPatientFname(String patientFname) {
        this.patientFname = patientFname;
    }

    public String getPatientLname() {
        return patientLname;
    }
    public void setPatientLname(String patientLname) {
        this.patientLname = patientLname;
    }

    public String getPatientMiddleName() {
        return patientMiddleName;
    }
    public void setPatientMiddleName(String patientMiddleName) {
        this.patientMiddleName = patientMiddleName;
    }

    public Gender getGender() {
        return gender;
    }
    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public String getPhone() {
        return phone;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Branch getBranch() {
        return branch;
    }
    public void setBranch(Branch branch) {
        this.branch = branch;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }
    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }

    public String getGenderDetail() {
        return genderDetail;
    }
    public void setGenderDetail(String genderDetail) {
        this.genderDetail = genderDetail;
    }

    public String getPatientNickname() {
        return patientNickname;
    }
    public void setPatientNickname(String patientNickname) {
        this.patientNickname = patientNickname;
    }

    public String getNationalId() {
        return nationalId;
    }
    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public String getPassportNo() {
        return passportNo;
    }
    public void setPassportNo(String passportNo) {
        this.passportNo = passportNo;
    }

    public LocalDate getBirthdate() {
        return birthdate;
    }
    public void setBirthdate(LocalDate birthdate) {
        this.birthdate = birthdate;
    }

    public BloodGroup getBloodGroup() {
        return bloodGroup;
    }
    public void setBloodGroup(BloodGroup bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public String getNationality() {
        return nationality;
    }
    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getAddress() {
        return address;
    }
    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isDrugAllergy() {
        return drugAllergy;
    }
    public void setDrugAllergy(boolean drugAllergy) {
        this.drugAllergy = drugAllergy;
    }

    public boolean isFoodAllergy() {
        return foodAllergy;
    }
    public void setFoodAllergy(boolean foodAllergy) {
        this.foodAllergy = foodAllergy;
    }

    public String getChronicDisease() {
        return chronicDisease;
    }
    public void setChronicDisease(String chronicDisease) {
        this.chronicDisease = chronicDisease;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }
    public void setEmergencyContactName(String emergencyContactName) {
        this.emergencyContactName = emergencyContactName;
    }

    public String getEmergencyContactRelationship() {
        return emergencyContactRelationship;
    }
    public void setEmergencyContactRelationship(String emergencyContactRelationship) {
        this.emergencyContactRelationship = emergencyContactRelationship;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }
    public void setEmergencyContactPhone(String emergencyContactPhone) {
        this.emergencyContactPhone = emergencyContactPhone;
    }

    public boolean isPrivacyConsent() {
        return privacyConsent;
    }
    public void setPrivacyConsent(boolean privacyConsent) {
        this.privacyConsent = privacyConsent;
    }

    public Long getRegisteredBy() {
        return registeredBy;
    }
    public void setRegisteredBy(Long registeredBy) {
        this.registeredBy = registeredBy;
    }

    public Instant getPatientUpdateAt() {
        return patientUpdateAt;
    }
    public void setPatientUpdateAt(Instant patientUpdateAt) {
        this.patientUpdateAt = patientUpdateAt;
    }

    public Long getPatientUpdateBy() {
        return patientUpdateBy;
    }
    public void setPatientUpdateBy(Long patientUpdateBy) {
        this.patientUpdateBy = patientUpdateBy;
    }

    public MasterDataValue getCustomerGroup() {
        return customerGroup;
    }
    public void setCustomerGroup(MasterDataValue customerGroup) {
        this.customerGroup = customerGroup;
    }

    public MasterDataValue getReferralChannel() {
        return referralChannel;
    }
    public void setReferralChannel(MasterDataValue referralChannel) {
        this.referralChannel = referralChannel;
    }

    public MasterDataValue getInsuranceType() {
        return insuranceType;
    }
    public void setInsuranceType(MasterDataValue insuranceType) {
        this.insuranceType = insuranceType;
    }

    public MasterDataValue getInsuranceCompany() {
        return insuranceCompany;
    }
    public void setInsuranceCompany(MasterDataValue insuranceCompany) {
        this.insuranceCompany = insuranceCompany;
    }
}
