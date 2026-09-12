package com.sabyeguyz.clinic.payment;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "payment_methods")
public class PaymentMethod {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pm_gen")
    @SequenceGenerator(name = "pm_gen", sequenceName = "payment_method_id_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    private String name;
    private boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    protected PaymentMethod() {}

    public PaymentMethod(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public void update(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public void deactivate() {
        active = false;
    }

    public void activate() {
        active = true;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
