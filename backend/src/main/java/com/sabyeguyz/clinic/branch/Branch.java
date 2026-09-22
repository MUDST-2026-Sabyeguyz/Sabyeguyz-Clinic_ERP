package com.sabyeguyz.clinic.branch;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "branches")
public class Branch {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "branch_id_generator")
    @SequenceGenerator(name = "branch_id_generator", sequenceName = "branch_id_seq", allocationSize = 1)
    private Long id;
    private String code;
    @jakarta.persistence.Column(name = "numeric_code", length = 2, unique = true)
    private String numericCode;
    private String name;
    private boolean active;

    protected Branch() {}

    public Branch(String code, String numericCode, String name) {
        this.code = code;
        this.numericCode = numericCode;
        this.name = name;
        this.active = true;
    }

    public void update(String code, String numericCode, String name) {
        this.code = code;
        this.numericCode = numericCode;
        this.name = name;
    }

    public void deactivate() { this.active = false; }
    public void activate() { this.active = true; }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getNumericCode() { return numericCode; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
