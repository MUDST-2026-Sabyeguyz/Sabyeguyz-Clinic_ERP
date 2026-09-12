package com.sabyeguyz.clinic.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "courses")
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "course_gen")
    @SequenceGenerator(name = "course_gen", sequenceName = "course_id_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;
    private String name;
    private BigDecimal price;
    private int sessions;
    @Column(name = "bonus_sessions")
    private int bonusSessions;
    @Column(name = "validity_days")
    private int validityDays;
    private boolean active = true;
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected Course() {}

    public Course(String code, String name, BigDecimal price, int sessions, int bonusSessions, int validityDays) {
        this.code = code;
        this.name = name;
        this.price = price;
        this.sessions = sessions;
        this.bonusSessions = bonusSessions;
        this.validityDays = validityDays;
    }

    public void update(String code, String name, BigDecimal price, int sessions, int bonusSessions, int validityDays) {
        this.code = code;
        this.name = name;
        this.price = price;
        this.sessions = sessions;
        this.bonusSessions = bonusSessions;
        this.validityDays = validityDays;
    }

    public void deactivate() { active = false; }
    public void activate() { active = true; }
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public int getSessions() { return sessions; }
    public int getBonusSessions() { return bonusSessions; }
    public int getValidityDays() { return validityDays; }
    public boolean isActive() { return active; }
}
