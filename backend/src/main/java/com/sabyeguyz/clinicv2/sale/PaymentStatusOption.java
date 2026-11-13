package com.sabyeguyz.clinicv2.sale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_status_option")
public class PaymentStatusOption {
    @Id
    @Column(name = "status_code", length = 20)
    private String code;

    @Column(name = "display_name", length = 50, nullable = false)
    private String label;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    protected PaymentStatusOption() {}

    public String getCode() { return code; }
    public String getLabel() { return label; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
