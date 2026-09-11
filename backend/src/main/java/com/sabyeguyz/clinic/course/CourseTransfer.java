package com.sabyeguyz.clinic.course;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "course_transfers")
public class CourseTransfer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "source_purchase_id")
    private CoursePurchase source;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "destination_purchase_id")
    private CoursePurchase destination;
    private int quantity;
    private Long actorId;
    private String reason;
    private Instant createdAt;
    protected CourseTransfer() {}
    CourseTransfer(CoursePurchase source, CoursePurchase destination, int quantity, Long actorId, String reason) {
        this.source = source; this.destination = destination; this.quantity = quantity;
        this.actorId = actorId; this.reason = reason; this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public CoursePurchase getSource() { return source; }
    public CoursePurchase getDestination() { return destination; }
    public int getQuantity() { return quantity; }
    public Long getActorId() { return actorId; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
