package com.sabyeguyz.clinic.commission;

import com.sabyeguyz.clinic.staff.Staff;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(
        name = "commission_closings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"closing_month", "employee_id"}))
public class CommissionClosing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "closing_month")
    private LocalDate month;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Staff employee;

    @Column(name = "monthly_course_sales")
    private BigDecimal sales;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_scheme_id")
    private CommissionScheme scheme;

    @Column(name = "commission_scheme_version")
    private String version;

    @Column(name = "calculated_commission_rate")
    private BigDecimal rate;

    @Column(name = "locked_commission_rate")
    private BigDecimal lockedRate;

    @Column(name = "closing_status")
    private String status = "OPEN";

    @Column(name = "closed_at")
    private Instant closedAt;

    protected CommissionClosing() {}

    public CommissionClosing(
            LocalDate m, Staff e, BigDecimal s, CommissionScheme sc, BigDecimal r) {
        month = m;
        employee = e;
        sales = s;
        scheme = sc;
        version = sc.getVersion();
        rate = r;
        lockedRate = r;
    }

    public void close() {
        status = "CLOSED";
        closedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public LocalDate getMonth() {
        return month;
    }

    public Staff getEmployee() {
        return employee;
    }

    public BigDecimal getSales() {
        return sales;
    }

    public String getVersion() {
        return version;
    }

    public BigDecimal getLockedRate() {
        return lockedRate;
    }

    public String getStatus() {
        return status;
    }
}
