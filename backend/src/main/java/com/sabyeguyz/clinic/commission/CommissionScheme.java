package com.sabyeguyz.clinic.commission;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "commission_schemes", uniqueConstraints = @UniqueConstraint(columnNames = {"name", "version"}))
public class CommissionScheme {
    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "commission_scheme_gen")
    @SequenceGenerator(name = "commission_scheme_gen", sequenceName = "commission_scheme_id_seq", allocationSize = 1)
    private Long id;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 40) private String version;
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    protected CommissionScheme() {}
    public CommissionScheme(String name, String version, LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.name = name; this.version = version; this.effectiveFrom = effectiveFrom; this.effectiveTo = effectiveTo;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public boolean isActive() { return active; }
    public void deactivate() { active = false; }
    public void activate() { active = true; }
    public void update(String name, String version, LocalDate from, LocalDate to) { this.name=name; this.version=version; this.effectiveFrom=from; this.effectiveTo=to; }
}
