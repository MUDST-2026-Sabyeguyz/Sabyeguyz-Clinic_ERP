package com.sabyeguyz.clinic.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "services")
public class ServiceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "service_id_generator")
    @SequenceGenerator(name = "service_id_generator", sequenceName = "service_id_seq", allocationSize = 1)
    private Long id;

    private String code;
    private String name;

    @Column(name = "default_duration_minutes")
    private int defaultDurationMinutes;

    private boolean active;

    @Column(name = "created_at")
    private Instant createdAt;

    protected ServiceItem() {}

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getDefaultDurationMinutes() { return defaultDurationMinutes; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}