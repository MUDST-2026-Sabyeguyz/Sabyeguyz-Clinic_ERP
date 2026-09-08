package com.sabyeguyz.clinic.sale;

import com.sabyeguyz.clinic.catalog.Course;
import com.sabyeguyz.clinic.catalog.ServiceItem;
import com.sabyeguyz.clinic.course.CoursePurchase;
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
import java.math.BigDecimal;
import java.time.Instant;

/**
 * บรรทัดหนึ่งในใบเสร็จ เก็บชื่อและราคาเป็น snapshot ณ วันที่ขาย
 * แก้ราคาใน master ทีหลังจึงไม่กระทบใบเสร็จที่ออกไปแล้ว
 */
@Entity
@Table(name = "sale_items")
public class SaleItem {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sale_item_id_generator")
    @SequenceGenerator(name = "sale_item_id_generator", sequenceName = "sale_item_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", length = 20)
    private SaleItemType itemType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ServiceItem service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    /** ใบซื้อคอร์สที่บรรทัดนี้สร้าง ใช้ตามรอยกลับตอนยกเลิกใบเสร็จ */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_purchase_id")
    private CoursePurchase coursePurchase;

    private String description;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    private int quantity;

    @Column(name = "line_total")
    private BigDecimal lineTotal;

    @Column(name = "created_at")
    private Instant createdAt;

    protected SaleItem() {}

    private SaleItem(Sale sale, SaleItemType itemType, ServiceItem service, Course course,
                     String description, BigDecimal unitPrice, int quantity) {
        this.sale = sale;
        this.itemType = itemType;
        this.service = service;
        this.course = course;
        this.description = description;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        this.createdAt = Instant.now();
    }

    public static SaleItem ofService(Sale sale, SaleItemType type, ServiceItem service, int quantity) {
        return new SaleItem(sale, type, service, null, service.getName(), service.getPrice(), quantity);
    }

    public static SaleItem ofCourse(Sale sale, Course course) {
        return new SaleItem(sale, SaleItemType.COURSE, null, course, course.getName(), course.getPrice(), 1);
    }

    public void linkCoursePurchase(CoursePurchase purchase) {
        this.coursePurchase = purchase;
    }

    public Long getId() { return id; }
    public Sale getSale() { return sale; }
    public SaleItemType getItemType() { return itemType; }
    public ServiceItem getService() { return service; }
    public Course getCourse() { return course; }
    public CoursePurchase getCoursePurchase() { return coursePurchase; }
    public String getDescription() { return description; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public Instant getCreatedAt() { return createdAt; }
}
