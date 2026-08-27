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
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void assignHn() { this.hn = "%s-%06d".formatted(branch.getCode(), id); }
    public Long getId() { return id; }
    public String getHn() { return hn; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public boolean isActive() { return active; }
}
