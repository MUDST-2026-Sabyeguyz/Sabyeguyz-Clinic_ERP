package com.sabyeguyz.clinic.patient;

import com.sabyeguyz.clinic.branch.Branch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "patients")
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "patient_id_generator")
    @SequenceGenerator(name = "patient_id_generator", sequenceName = "patient_id_seq", allocationSize = 1)
    private Long id;
    private String hn;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;
    private String username;
    private String email;
    @Column(name = "password_hash")
    private String passwordHash;
    private String title;
    @Column(name = "full_name")
    private String fullName;
    @Column(name = "birth_date")
    private LocalDate birthDate;
    private String gender;
    private String phone;
    @Column(name = "identity_document_type")
    private String identityDocumentType;
    @Column(name = "identity_number")
    private String identityNumber;
    private String address;
    @Column(name = "drug_allergy")
    private boolean drugAllergy;
    @Column(name = "food_allergy")
    private boolean foodAllergy;
    @Column(name = "chronic_disease")
    private String chronicDisease;
    @Column(name = "emergency_contact_name")
    private String emergencyContactName;
    @Column(name = "emergency_contact_relationship")
    private String emergencyContactRelationship;
    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;
    @Column(name = "privacy_consent")
    private boolean privacyConsent;
    private String nickname;
    @Column(name = "blood_group") private String bloodGroup;
    private String nationality;
    @Column(name = "customer_type") private String customerType;
    @Column(name = "customer_source") private String customerSource;
    @Column(name = "insurance_company") private String insuranceCompany;
    private boolean active;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Patient() {}

    public Patient(Branch branch, PatientRegistrationRequest request, String passwordHash) {
        this.branch = branch;
        this.username = request.username();
        this.email = request.email();
        this.passwordHash = passwordHash;
        this.title = request.title();
        this.fullName = request.fullName();
        this.birthDate = request.birthDate();
        this.gender = request.gender();
        this.phone = request.phone();
        this.identityDocumentType = request.identityDocumentType();
        this.identityNumber = request.identityNumber();
        this.address = request.address();
        this.drugAllergy = request.drugAllergy();
        this.foodAllergy = request.foodAllergy();
        this.chronicDisease = request.chronicDisease();
        this.emergencyContactName = request.emergencyContactName();
        this.emergencyContactRelationship = request.emergencyContactRelationship();
        this.emergencyContactPhone = request.emergencyContactPhone();
        this.privacyConsent = request.privacyConsent();
        this.nickname = request.nickname(); this.bloodGroup = request.bloodGroup(); this.nationality = request.nationality();
        this.customerType = request.customerType(); this.customerSource = request.customerSource(); this.insuranceCompany = request.insuranceCompany();
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void assignHn(String generatedHn) {
        if (this.hn == null) this.hn = generatedHn;
    }
    public void update(PatientUpdateRequest request) {
        this.title = request.title(); this.fullName = request.fullName().trim(); this.birthDate = request.birthDate();
        this.gender = request.gender(); this.phone = request.phone().trim();
        this.identityDocumentType = request.identityDocumentType().trim(); this.identityNumber = request.identityNumber().trim();
        this.address = request.address().trim(); this.drugAllergy = request.drugAllergy(); this.foodAllergy = request.foodAllergy();
        this.chronicDisease = request.chronicDisease(); this.emergencyContactName = request.emergencyContactName().trim();
        this.emergencyContactRelationship = request.emergencyContactRelationship().trim();
        this.emergencyContactPhone = request.emergencyContactPhone().trim(); this.privacyConsent = request.privacyConsent();
        if (request.nickname() != null) this.nickname = request.nickname().trim();
        if (request.email() != null) this.email = request.email().trim();
        if (request.bloodGroup() != null) this.bloodGroup = request.bloodGroup().trim();
        if (request.nationality() != null) this.nationality = request.nationality().trim();
        if (request.customerType() != null) this.customerType = request.customerType().trim();
        if (request.customerSource() != null) this.customerSource = request.customerSource().trim();
        if (request.insuranceCompany() != null) this.insuranceCompany = request.insuranceCompany().trim();
        this.active = request.active(); this.updatedAt = Instant.now();
    }
    public Long getId() { return id; }
    public String getHn() { return hn; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public boolean isActive() { return active; }
    public Branch getBranch() { return branch; }
    public String getTitle() { return title; }
    public LocalDate getBirthDate() { return birthDate; }
    public String getGender() { return gender; }
    public String getIdentityDocumentType() { return identityDocumentType; }
    public String getIdentityNumber() { return identityNumber; }
    public String getAddress() { return address; }
    public boolean isDrugAllergy() { return drugAllergy; }
    public boolean isFoodAllergy() { return foodAllergy; }
    public String getChronicDisease() { return chronicDisease; }
    public String getEmergencyContactName() { return emergencyContactName; }
    public String getEmergencyContactRelationship() { return emergencyContactRelationship; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public boolean isPrivacyConsent() { return privacyConsent; }
    public String getNickname() { return nickname; }
    public String getEmail() { return email; }
    public String getBloodGroup() { return bloodGroup; }
    public String getNationality() { return nationality; }
    public String getCustomerType() { return customerType; }
    public String getCustomerSource() { return customerSource; }
    public String getInsuranceCompany() { return insuranceCompany; }
}
