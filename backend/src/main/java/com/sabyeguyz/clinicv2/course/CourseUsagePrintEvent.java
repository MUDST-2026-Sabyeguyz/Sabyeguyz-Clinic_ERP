package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.enums.PrintEventType;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Issued happens once per print; every later paper copy is a Reprint. */
@Entity
@Table(name = "course_usage_print_event")
public class CourseUsagePrintEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "print_event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "print_id")
    private CourseUsagePrint print;

    @Column(name = "printed_at", nullable = false)
    private Instant printedAt;

    @Column(name = "printed_by", nullable = false)
    private Long printedBy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "event_type", nullable = false, columnDefinition = "event_type_enum")
    private PrintEventType eventType;

    public CourseUsagePrintEvent() {}

    public Long getId() { return id; }
    public CourseUsagePrint getPrint() { return print; }
    public void setPrint(CourseUsagePrint print) { this.print = print; }
    public Instant getPrintedAt() { return printedAt; }
    public void setPrintedAt(Instant printedAt) { this.printedAt = printedAt; }
    public Long getPrintedBy() { return printedBy; }
    public void setPrintedBy(Long printedBy) { this.printedBy = printedBy; }
    public PrintEventType getEventType() { return eventType; }
    public void setEventType(PrintEventType eventType) { this.eventType = eventType; }
}
