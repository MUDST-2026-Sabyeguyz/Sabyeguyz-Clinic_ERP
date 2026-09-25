package com.sabyeguyz.clinicv2.patient;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.BloodGroup;
import com.sabyeguyz.clinicv2.enums.CustomerType;
import com.sabyeguyz.clinicv2.enums.Gender;
import com.sabyeguyz.clinicv2.masterdata.MasterDataValue;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "patient")
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patient_id")
    private Long id;

    /** Format YYBBMMNNNN. */
    @Column(name = "patient_hn", length = 10, unique = true)
    private String patientHn;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "customer_type", nullable = false)
    private CustomerType customerType;

    /** ด.ช., ด.ญ., นาย, นาง, นางสาว — plain string since the values are not valid Java identifiers. */
    @Column(length = 20, nullable = false)
    private String title;

    @Column(name = "patient_fname", length = 100, nullable = false)
    private String patientFname;

    @Column(name = "patient_lname", length = 100, nullable = false)
    private String patientLname;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private Gender gender;

    @Column(length = 20, nullable = false)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "gender_detail", length = 50)
    private String genderDetail;

    @Column(name = "patient_nickname", length = 50)
    private String patientNickname;

    @Column(name = "national_id", length = 13)
    private String nationalId;

    @Column(name = "passport_no", length = 20)
    private String passportNo;

    private LocalDate birthdate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "blood_group")
    private BloodGroup bloodGroup;

    @Column(length = 50)
    private String nationality;

    @Column(length = 255)
    private String address;

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

    protected Patient() {}

    public Long getId() { return id; }
    public String getPatientHn() { return patientHn; }
    public void setPatientHn(String patientHn) { this.patientHn = patientHn; }
    public CustomerType getCustomerType() { return customerType; }
    public void setCustomerType(CustomerType customerType) { this.customerType = customerType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPatientFname() { return patientFname; }
    public void setPatientFname(String patientFname) { this.patientFname = patientFname; }
    public String getPatientLname() { return patientLname; }
    public void setPatientLname(String patientLname) { this.patientLname = patientLname; }
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }
    public String getGenderDetail() { return genderDetail; }
    public void setGenderDetail(String genderDetail) { this.genderDetail = genderDetail; }
    public String getPatientNickname() { return patientNickname; }
    public void setPatientNickname(String patientNickname) { this.patientNickname = patientNickname; }
    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }
    public String getPassportNo() { return passportNo; }
    public void setPassportNo(String passportNo) { this.passportNo = passportNo; }
    public LocalDate getBirthdate() { return birthdate; }
    public void setBirthdate(LocalDate birthdate) { this.birthdate = birthdate; }
    public BloodGroup getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(BloodGroup bloodGroup) { this.bloodGroup = bloodGroup; }
    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Long getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(Long registeredBy) { this.registeredBy = registeredBy; }
    public Instant getPatientUpdateAt() { return patientUpdateAt; }
    public void setPatientUpdateAt(Instant patientUpdateAt) { this.patientUpdateAt = patientUpdateAt; }
    public Long getPatientUpdateBy() { return patientUpdateBy; }
    public void setPatientUpdateBy(Long patientUpdateBy) { this.patientUpdateBy = patientUpdateBy; }
    public MasterDataValue getCustomerGroup() { return customerGroup; }
    public void setCustomerGroup(MasterDataValue customerGroup) { this.customerGroup = customerGroup; }
    public MasterDataValue getReferralChannel() { return referralChannel; }
    public void setReferralChannel(MasterDataValue referralChannel) { this.referralChannel = referralChannel; }
    public MasterDataValue getInsuranceType() { return insuranceType; }
    public void setInsuranceType(MasterDataValue insuranceType) { this.insuranceType = insuranceType; }
    public MasterDataValue getInsuranceCompany() { return insuranceCompany; }
    public void setInsuranceCompany(MasterDataValue insuranceCompany) { this.insuranceCompany = insuranceCompany; }
}
