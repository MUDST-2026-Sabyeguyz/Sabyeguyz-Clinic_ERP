package com.sabyeguyz.clinic.course;

import com.sabyeguyz.clinic.appointment.Appointment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * รายการเดินบัญชีของคอร์ส — เขียนเพิ่มอย่างเดียว ห้ามแก้หรือลบ
 * การยกเลิกทำโดยสร้าง movement ชนิด {@link CourseMovementType#REVERSAL} ที่ชี้กลับไปยังรายการเดิม
 */
@Entity
@Table(name = "course_movements")
public class CourseMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "course_movement_id_generator")
    @SequenceGenerator(name = "course_movement_id_generator", sequenceName = "course_movement_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_purchase_id")
    private CoursePurchase coursePurchase;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20)
    private CourseMovementType type;

    private int quantity;

    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_movement_id")
    private CourseMovement reversesMovement;

    @Column(name = "created_at")
    private Instant createdAt;

    protected CourseMovement() {}

    private CourseMovement(CoursePurchase coursePurchase, CourseMovementType type, int quantity,
                           String reason, Appointment appointment, CourseMovement reversesMovement) {
        this.coursePurchase = coursePurchase;
        this.type = type;
        this.quantity = quantity;
        this.reason = reason;
        this.appointment = appointment;
        this.reversesMovement = reversesMovement;
        this.createdAt = Instant.now();
    }

    public static CourseMovement purchase(CoursePurchase purchase) {
        return new CourseMovement(purchase, CourseMovementType.PURCHASE, purchase.totalSessions(),
                "ซื้อคอร์ส " + purchase.getCourseCode(), null, null);
    }

    public static CourseMovement usage(CoursePurchase purchase, Appointment appointment, String reason) {
        return new CourseMovement(purchase, CourseMovementType.USAGE, -1, reason, appointment, null);
    }

    public static CourseMovement reversalOf(CourseMovement original, String reason) {
        return new CourseMovement(original.getCoursePurchase(), CourseMovementType.REVERSAL,
                -original.getQuantity(), reason, original.getAppointment(), original);
    }

    /** ใช้ตอนยกเลิกใบซื้อทั้งใบ: ล้างสิทธิ์คงเหลือให้เป็นศูนย์ */
    public static CourseMovement cancellation(CoursePurchase purchase, int remaining, String reason) {
        return new CourseMovement(purchase, CourseMovementType.REVERSAL, -remaining, reason, null, null);
    }

    public Long getId() { return id; }
    public CoursePurchase getCoursePurchase() { return coursePurchase; }
    public CourseMovementType getType() { return type; }
    public int getQuantity() { return quantity; }
    public String getReason() { return reason; }
    public Appointment getAppointment() { return appointment; }
    public CourseMovement getReversesMovement() { return reversesMovement; }
    public Instant getCreatedAt() { return createdAt; }
}
