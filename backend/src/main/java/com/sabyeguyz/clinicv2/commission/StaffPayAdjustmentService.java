package com.sabyeguyz.clinicv2.commission;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.appointment.PatientVisitRepository;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.enums.AdjustmentStatus;
import com.sabyeguyz.clinicv2.enums.ApplicationStatus;
import com.sabyeguyz.clinicv2.enums.PaymentStatus;
import com.sabyeguyz.clinicv2.sale.SalesTransaction;
import com.sabyeguyz.clinicv2.sale.SalesTransactionRepository;
import com.sabyeguyz.clinicv2.staff.Staff;
import com.sabyeguyz.clinicv2.staff.StaffRepository;

/**
 * Corrections of money that is already paid (Data Dictionary: Staff_pay_adjustment, Adjustment_application). A payment
 * that is Paid never changes and neither does a locked rate: a mistake is fixed by an adjustment, paid or deducted in a
 * later month.
 *
 * <ul>
 *   <li>New Manager adjustments name a paid source, a reason and a starting month. They enter payout calculation
 *       immediately; deductions carry their consent reference and any remainder is scheduled automatically.
 *       Historic manually scheduled adjustments retain their original approval flow.</li>
 *   <li>An application becomes Applied only when the payment it sits on is paid; then it can no longer be changed.
 *       What a payment could not deduct stays on the adjustment (remaining) for a later month.</li>
 *   <li>Termination cancels the unpaid balance while preserving already paid applications.</li>
 * </ul>
 */
@Service
public class StaffPayAdjustmentService {
    private final StaffPayAdjustmentRepository adjustments;
    private final AdjustmentApplicationRepository applications;
    private final StaffMonthlyPaymentRepository payments;
    private final StaffMonthlyPaymentService paymentService;
    private final StaffRepository staff;
    private final CommissionAllocationLedgerRepository allocations;
    private final SingleVisitFeeLedgerRepository singleVisitFees;
    private final CourseRepository courses;
    private final PatientVisitRepository visits;
    private final SalesTransactionRepository sales;

    public StaffPayAdjustmentService(
            StaffPayAdjustmentRepository adjustments,
            AdjustmentApplicationRepository applications,
            StaffMonthlyPaymentRepository payments,
            StaffMonthlyPaymentService paymentService,
            StaffRepository staff,
            CommissionAllocationLedgerRepository allocations,
            SingleVisitFeeLedgerRepository singleVisitFees,
            CourseRepository courses,
            PatientVisitRepository visits,
            SalesTransactionRepository sales) {
        this.adjustments = adjustments;
        this.applications = applications;
        this.payments = payments;
        this.paymentService = paymentService;
        this.staff = staff;
        this.allocations = allocations;
        this.singleVisitFees = singleVisitFees;
        this.courses = courses;
        this.visits = visits;
        this.sales = sales;
    }

    public record CreateRequest(Long staffId, String originalMonth, String appliedMonth, BigDecimal amount, String reason,
                                 Long allocationId, Long singleVisitFeeId, Long courseId, Long visitId, Long saleTransactionId,
                                 String branchCode, String approvalReference) {}

    public record SourceOption(Long id, String label, String month) {}

    @Transactional(readOnly = true)
    public List<SourceOption> sources(Long staffId, Long branchId, String type) {
        require(staffId != null && branchId != null, "ต้องเลือกนักกายภาพและสาขา");
        java.util.Set<String> paidMonths = payments.findByStaffIdOrderByPaymentMonthDesc(staffId)
                .stream().filter(payment -> payment.getBranch() != null
                        && branchId.equals(payment.getBranch().getId())
                        && payment.getPaymentStatus() == PaymentStatus.Paid)
                .map(StaffMonthlyPayment::getPaymentMonth).collect(java.util.stream.Collectors.toSet());
        List<SourceOption> options = switch (type) {
            case "visit" -> visits.findTop100ByTreatingStaffIdAndBranchIdOrderByVisitDateDesc(staffId, branchId)
                    .stream().map(row -> new SourceOption(row.getId(),
                            "VIS-" + row.getId() + " · " + row.getPatient().getPatientFname() + " "
                                    + row.getPatient().getPatientLname() + " · " + row.getVisitDate(),
                            row.getVisitDate().atZone(java.time.ZoneId.of("Asia/Bangkok")).toLocalDate().toString().substring(0, 7)))
                    .toList();
            case "course" -> courses.findTop100BySellerStaffIdAndBranchIdOrderByIdDesc(staffId, branchId)
                    .stream().map(row -> new SourceOption(row.getId(),
                            row.getCourseNo() + " · " + row.getCourseName(), row.getSaleMonth()))
                    .toList();
            case "commission" -> allocations.findAdjustmentSources(staffId, branchId,
                            org.springframework.data.domain.PageRequest.of(0, 100))
                    .stream().map(row -> new SourceOption(row.getId(),
                            "COM-" + row.getId() + " · " + row.getPatient().getPatientFname() + " "
                                    + row.getPatient().getPatientLname() + " · " + row.getVisitDate(),
                            row.getVisitDate().toString().substring(0, 7)))
                    .toList();
            default -> throw new IllegalArgumentException("ไม่รู้จักประเภทรายการอ้างอิง");
        };
        return options.stream().filter(option -> paidMonths.contains(option.month())).toList();
    }

    @Transactional(readOnly = true)
    public List<StaffPayAdjustment> list(Long staffId) {
        return staffId == null ? adjustments.findAllByOrderByIdDesc() : adjustments.findByStaffIdOrderByIdDesc(staffId);
    }

    @Transactional(readOnly = true)
    public StaffPayAdjustment find(Long id) {
        return adjustments.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบใบปรับยอด"));
    }

    @Transactional(readOnly = true)
    public List<AdjustmentApplication> applicationsOf(Long adjustmentId) {
        return applications.findByAdjustmentIdOrderByIdAsc(adjustmentId);
    }

    @Transactional
    public StaffPayAdjustment create(CreateRequest request, com.sabyeguyz.clinicv2.branch.Branch branch, Long actorId) {
        require(request.staffId() != null, "ต้องระบุพนักงาน");
        Staff person = staff.findById(request.staffId()).orElseThrow(() -> new IllegalArgumentException("ไม่พบพนักงาน"));
        require(person.isStaffStatus() && person.getTerminationDate() == null
                && person.getPosition() == com.sabyeguyz.clinicv2.enums.StaffPosition.Therapist,
                "เลือกได้เฉพาะนักกายภาพที่ยังใช้งานอยู่");
        require(branch != null && branch.getId() != null, "ต้องระบุสาขา");
        require(request.amount() != null && request.amount().signum() != 0, "ยอดปรับต้องไม่เป็น 0");
        require(request.amount().stripTrailingZeros().scale() <= 2, "ยอดปรับมีทศนิยมได้ไม่เกิน 2 ตำแหน่ง");
        require(request.reason() != null && !request.reason().isBlank(), "ต้องระบุเหตุผล");
        require(request.reason().trim().length() <= 255, "เหตุผลยาวเกินไป");
        require(request.approvalReference() == null || request.approvalReference().trim().length() <= 255,
                "เลขอ้างอิงหลักฐานยาวเกินไป");

        CommissionAllocationLedger allocation = request.allocationId() == null ? null : allocations.findById(request.allocationId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบรายการ allocation"));
        SingleVisitFeeLedger fee = request.singleVisitFeeId() == null ? null : singleVisitFees.findById(request.singleVisitFeeId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบรายการค่ามือรายครั้ง"));
        Course course = request.courseId() == null ? null : courses.findById(request.courseId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบคอร์ส"));
        PatientVisit visit = request.visitId() == null ? null : visits.findById(request.visitId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบ Visit"));
        SalesTransaction sale = request.saleTransactionId() == null ? null : sales.findById(request.saleTransactionId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบรายการขาย"));
        require(allocation != null || fee != null || course != null || visit != null || sale != null,
                "ต้องอ้างอิงต้นทางอย่างน้อยหนึ่งรายการ (allocation, ค่ามือรายครั้ง, คอร์ส, Visit หรือรายการขาย)");
        checkSources(person, allocation, fee, course, visit, sale);

        String original = request.originalMonth();

        if (original == null) {
            original = allocation != null ? allocation.getVisitDate().toString().substring(0, 7)
                    : fee != null ? fee.getServiceDate().toString().substring(0, 7) : null;
        }

        require(original != null, "ต้องระบุเดือนที่เกิดข้อผิดพลาด (originalMonth)");
        StaffMonthlyPaymentService.requireMonth(original);
        String sourceMonth = allocation != null ? allocation.getVisitDate().toString().substring(0, 7)
                : fee != null ? fee.getServiceDate().toString().substring(0, 7)
                : visit != null ? visit.getVisitDate().atZone(java.time.ZoneId.of("Asia/Bangkok"))
                        .toLocalDate().toString().substring(0, 7)
                : course != null ? course.getSaleMonth() : null;
        require(sourceMonth == null || sourceMonth.equals(original), "งวดต้นทางต้องตรงกับรายการอ้างอิง");
        boolean allocationBranchMatches = allocation == null
                || (allocation.getOwnerStaff().getId().equals(person.getId())
                    && branch.getId().equals(allocation.getCourse().getBranch().getId()))
                || (allocation.getTreatingStaff().getId().equals(person.getId())
                    && branch.getId().equals(allocation.getVisitUsage().getVisit().getBranch().getId()));
        Long sourceBranch = fee != null ? fee.getVisit().getBranch().getId()
                : visit != null ? visit.getBranch().getId()
                : course != null ? course.getBranch().getId() : null;
        require(allocationBranchMatches && (sourceBranch == null || sourceBranch.equals(branch.getId())),
                "รายการอ้างอิงต้องอยู่ในสาขาที่เลือก");
        require(payments.findByStaffIdAndPaymentMonthAndBranchId(person.getId(), original, branch.getId())
                .filter(payment -> payment.getPaymentStatus() == PaymentStatus.Paid).isPresent(),
                "งวดต้นทางยังไม่จ่าย กรุณาแก้ข้อมูลรายการรักษาและคำนวณค่ามือใหม่");
        String applied = request.appliedMonth() != null ? request.appliedMonth() : defaultAppliedMonth(person, original);
        StaffMonthlyPaymentService.requireMonth(applied);
        require(applied.compareTo(original) > 0, "เดือนที่นำไปใช้ต้องอยู่หลังเดือนที่เกิดข้อผิดพลาด");
        paymentService.lockAdjustmentMonth(branch.getId(), applied);
        var openMonth = paymentService.latestOpenMonth(branch.getId());
        require(openMonth.isPresent(), "ยังไม่มีงวดจ่ายที่เปิดอยู่ของสาขานี้");
        require(applied.equals(openMonth.get()) || applied.equals(YearMonth.parse(openMonth.get()).plusMonths(1).toString()),
                "เลือกได้เฉพาะงวดเปิดล่าสุดของสาขาหรือเดือนถัดไป");

        StaffPayAdjustment adjustment = new StaffPayAdjustment();
        adjustment.setStaff(person);
        adjustment.setBranch(branch);
        adjustment.setAutomatic(true);
        adjustment.setApprovalReference(request.approvalReference() == null ? null : request.approvalReference().trim());
        adjustment.setOriginalMonth(original);
        adjustment.setAppliedMonth(applied);
        adjustment.setAmount(request.amount());
        adjustment.setAllocation(allocation);
        adjustment.setSingleVisitFee(fee);
        adjustment.setCourse(course);
        adjustment.setVisit(visit);
        adjustment.setSaleTransaction(sale);
        adjustment.setReason(request.reason().trim());
        adjustment.setProcessedAmount(BigDecimal.ZERO);
        adjustment.setRemainingAmount(request.amount().abs());
        adjustment.setAdjustmentStatus(AdjustmentStatus.Approved);
        adjustment.setApprovedAt(Instant.now());
        adjustment.setApprovedBy(actorId);
        adjustment.setCreatedAt(Instant.now());
        adjustment.setCreatedBy(actorId);
        StaffPayAdjustment saved = adjustments.save(adjustment);
        if (applied.equals(openMonth.get())) {
            StaffMonthlyPayment payment = paymentService.openFor(person, applied, branch, actorId);
            require(payment.getPaymentStatus() == PaymentStatus.Open, "งวดที่เลือกต้องยังเปิดอยู่");
            paymentService.refresh(payment);
        }
        return saved;
    }

    /** The month after the original one, and after the last month already paid to the person. */
    private String defaultAppliedMonth(
            Staff person,
            String original) {
        String latest = original;

        for (StaffMonthlyPayment payment : payments.findByStaffIdOrderByPaymentMonthDesc(person.getId())) {
            if (payment.getPaymentStatus() == PaymentStatus.Paid
                    && payment.getPaymentMonth().compareTo(latest) > 0) {
                latest = payment.getPaymentMonth();
            }
        }

        return YearMonth.parse(latest).plusMonths(1).toString();
    }

    private void checkSources(
            Staff person,
            CommissionAllocationLedger allocation,
            SingleVisitFeeLedger fee,
            Course course,
            PatientVisit visit,
            SalesTransaction sale) {
        Long id = person.getId();

        if (allocation != null) {
            require(allocation.getOwnerStaff().getId().equals(id) || allocation.getTreatingStaff().getId().equals(id),
                    "allocation ที่อ้างไม่ใช่ของพนักงานคนนี้");
        }

        if (fee != null) require(fee.getStaff().getId().equals(id), "ค่ามือรายครั้งที่อ้างไม่ใช่ของพนักงานคนนี้");

        if (allocation != null && fee != null) {
            require(allocation.getVisitUsage().getVisit().getId()
                            .equals(fee.getVisit().getId()),
                    "allocation และค่ามือรายครั้งต้องอ้าง Visit เดียวกัน");
        }

        if (course != null) {
            require(course.getSellerStaff().getId().equals(id) || allocation != null || visit != null,
                    "คอร์สที่อ้างไม่ใช่ของพนักงานคนนี้");
        }

        if (visit != null && fee != null) require(fee.getVisit().getId().equals(visit.getId()), "Visit ไม่ตรงกับค่ามือรายครั้งที่อ้าง");

        if (allocation != null && course != null) {
            require(allocation.getCourse().getId().equals(course.getId()), "คอร์สไม่ตรงกับ allocation ที่อ้าง");
        }

        if (allocation != null && visit != null) {
            require(allocation.getVisitUsage().getVisit().getId().equals(visit.getId()), "Visit ไม่ตรงกับ allocation ที่อ้าง");
        }

        if (course != null && visit != null) {
            require(courses.existsUsageForCourseAndVisit(
                            course.getId(), visit.getId()),
                    "Visit ที่อ้างไม่มีประวัติใช้คอร์สต้นทางนี้");
        }

        if (course != null && fee != null) {
            require(
                    courses.existsUsageForCourseAndVisit(
                            course.getId(),
                            fee.getVisit().getId()),
                    "Visit ของค่ามือรายครั้งไม่มีประวัติใช้คอร์สที่อ้าง");
        }

        if (sale != null
                && sale.getTransactionType()
                == com.sabyeguyz.clinicv2.enums.TransactionType.SingleVisit) {
            require(sale.getVisit() != null,
                    "รายการขายรายครั้งต้องมี Visit ต้นทาง");
            Long saleVisitId = sale.getVisit().getId();

            if (visit != null) {
                require(saleVisitId.equals(visit.getId()),
                        "รายการขายไม่ตรงกับ Visit ที่อ้าง");
            }

            if (fee != null) {
                require(saleVisitId.equals(fee.getVisit().getId()),
                        "รายการขายไม่ตรงกับ Visit ของค่ามือรายครั้ง");
            }

            if (allocation != null) {
                require(saleVisitId.equals(
                                allocation.getVisitUsage().getVisit().getId()),
                        "รายการขายไม่ตรงกับ Visit ของ allocation");
            }
        }

        if (sale != null
                && allocation != null
                && sale.getTransactionType()
                == com.sabyeguyz.clinicv2.enums.TransactionType.CoursePurchase) {
            SalesTransaction courseSale =
                    allocation.getCourse().getSaleTransaction();
            require(
                    courseSale != null
                            && sale.getId().equals(courseSale.getId()),
                    "รายการขายไม่ตรงกับคอร์สของ allocation ที่อ้าง");
        }

        if (sale != null
                && sale.getTransactionType()
                == com.sabyeguyz.clinicv2.enums.TransactionType.CoursePurchase
                && (visit != null || fee != null)) {
            Course soldCourse = courses.findBySaleTransactionId(sale.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "ไม่พบคอร์สต้นทางของรายการขาย"));

            if (visit != null) {
                require(
                        courses.existsUsageForCourseAndVisit(
                                soldCourse.getId(), visit.getId()),
                        "Visit ไม่ได้ใช้คอร์สจากรายการขายที่อ้าง");
            }

            if (fee != null) {
                require(
                        courses.existsUsageForCourseAndVisit(
                                soldCourse.getId(), fee.getVisit().getId()),
                        "Visit ของค่ามือไม่ได้ใช้คอร์สจากรายการขายที่อ้าง");
            }
        }

        if (sale != null && course != null && course.getSaleTransaction() != null) {
            require(course.getSaleTransaction().getId().equals(sale.getId()), "รายการขายไม่ตรงกับคอร์สที่อ้าง");
        }

        if (course != null
                && course.getSellerStaff().getId().equals(id) == false
                && allocation == null
                && visit == null) {
            throw new IllegalArgumentException("คอร์สที่อ้างไม่ใช่ของพนักงานคนนี้");
        }

        if (visit != null && allocation == null && fee == null) {
            require(visit.getTreatingStaff() != null && visit.getTreatingStaff().getId().equals(id),
                    "Visit ที่อ้างไม่ได้รักษาโดยพนักงานคนนี้");
        }

        if (sale != null && course == null && allocation == null && fee == null && visit == null) {
            boolean mine = (sale.getSalespersonStaff() != null && sale.getSalespersonStaff().getId().equals(id))
                    || (sale.getTreatingStaff() != null && sale.getTreatingStaff().getId().equals(id));
            require(mine, "รายการขายที่อ้างไม่ใช่ของพนักงานคนนี้");
        }
    }

    @Transactional
    public StaffPayAdjustment approve(
            Long id,
            Long actorId) {
        StaffPayAdjustment adjustment = find(id);
        require(!adjustment.isAutomatic(), "รายการนี้พร้อมนำไปคำนวณแล้ว ไม่ต้องอนุมัติซ้ำ");
        require(adjustment.getAdjustmentStatus() == AdjustmentStatus.Pending, "อนุมัติได้เฉพาะใบปรับยอดที่รออนุมัติ");
        adjustment.setApprovedBy(actorId);
        adjustment.setApprovedAt(Instant.now());
        adjustment.setAdjustmentStatus(AdjustmentStatus.Approved);

        return adjustments.save(adjustment);
    }

    /** Cancels an adjustment nothing was applied from; its Draft applications are cancelled with it. */
    @Transactional
    public StaffPayAdjustment cancel(Long id) {
        StaffPayAdjustment adjustment = find(id);
        require(adjustment.getAdjustmentStatus() == AdjustmentStatus.Pending || adjustment.getAdjustmentStatus() == AdjustmentStatus.Approved
                        || (adjustment.isAutomatic() && adjustment.getAdjustmentStatus() == AdjustmentStatus.PartiallyApplied),
                "ยกเลิกได้เฉพาะใบปรับยอดที่ยังไม่มีการนำไปใช้จริง (ที่ใช้แล้วให้สร้างใบใหม่อ้างต้นทางเดิม)");
        cancelDrafts(adjustment);
        adjustment.setAdjustmentStatus(AdjustmentStatus.Cancelled);
        adjustment.setCancelledAt(Instant.now());
        return adjustments.save(adjustment);
    }

    @Transactional
    public void cancelOnTermination(Long staffId, Long actorId) {
        for (StaffPayAdjustment adjustment : adjustments.findByStaffIdOrderByIdDesc(staffId)) {
            if (adjustment.getRemainingAmount().signum() <= 0
                    || adjustment.getAdjustmentStatus() == AdjustmentStatus.Cancelled) continue;
            java.util.Set<StaffMonthlyPayment> affectedPayments = new java.util.LinkedHashSet<>();
            for (AdjustmentApplication application : applications.findByAdjustmentIdOrderByIdAsc(adjustment.getId())) {
                if (application.getApplicationStatus() != ApplicationStatus.Draft) continue;
                application.setApplicationStatus(ApplicationStatus.Cancelled);
                applications.save(application);
                affectedPayments.add(application.getMonthlyPayment());
            }
            adjustment.setAdjustmentStatus(AdjustmentStatus.Cancelled);
            adjustment.setCancellationReason("นักกายภาพลาออก");
            adjustment.setCancelledAt(Instant.now());
            adjustments.save(adjustment);
            for (StaffMonthlyPayment payment : affectedPayments) {
                if (payment.getPaymentStatus() == PaymentStatus.Closed) {
                    paymentService.reopen(payment.getId(), actorId);
                } else if (payment.getPaymentStatus() == PaymentStatus.Open) {
                    paymentService.refresh(payment);
                }
            }
        }
    }

    /** A deduction left over when the person has resigned is handled by the Manager outside the system. */
    @Transactional
    public StaffPayAdjustment externalReview(Long id) {
        StaffPayAdjustment adjustment = find(id);
        require(!adjustment.isAutomatic(), "รายการอัตโนมัติยกเลิกยอดคงเหลือเมื่อพนักงานลาออก");
        require(adjustment.getAmount().signum() < 0, "ส่งตรวจนอกระบบได้เฉพาะยอดหัก");
        require(adjustment.getAdjustmentStatus() == AdjustmentStatus.Approved
                || adjustment.getAdjustmentStatus() == AdjustmentStatus.PartiallyApplied, "ใบปรับยอดต้องได้รับอนุมัติและยังไม่ครบ");
        require(adjustment.getRemainingAmount().signum() > 0, "ไม่มียอดคงเหลือ");
        require(adjustment.getStaff().getTerminationDate() != null, "ใช้ได้เมื่อพนักงานลาออกแล้วเท่านั้น");
        cancelDrafts(adjustment);
        adjustment.setAdjustmentStatus(AdjustmentStatus.ExternalReview);

        return adjustments.save(adjustment);
    }

    /**
     * Reserves an amount of an approved adjustment on the payment of a month ({@code amountAbs} defaults to what is left
     * after the other Draft applications). The payment must be Open and the month not before the first intended month.
     */
    @Transactional
    public AdjustmentApplication createApplication(
            Long adjustmentId,
            String month,
            BigDecimal amountAbs,
            Branch branch,
            Long actorId) {
        require(branch != null && branch.getId() != null,
                "ต้องระบุสาขาของงวดจ่ายที่จะนำรายการปรับค่าตอบแทนไปใช้");
        StaffPayAdjustment adjustment = find(adjustmentId);
        require(!adjustment.isAutomatic(), "รายการนี้ระบบจัดสรรยอดให้อัตโนมัติ");
        checkSources(
                adjustment.getStaff(),
                adjustment.getAllocation(),
                adjustment.getSingleVisitFee(),
                adjustment.getCourse(),
                adjustment.getVisit(),
                adjustment.getSaleTransaction());
        Course payoutCourse = adjustment.getCourse();

        if (payoutCourse == null
                && adjustment.getSaleTransaction() != null
                && adjustment.getSaleTransaction().getTransactionType()
                == com.sabyeguyz.clinicv2.enums.TransactionType.CoursePurchase) {
            payoutCourse = courses.findBySaleTransactionId(
                            adjustment.getSaleTransaction().getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "ไม่พบคอร์สต้นทางของรายการขาย"));
        }

        if (adjustment.getSingleVisitFee() != null) {
            Branch sourceBranch = adjustment.getSingleVisitFee().getVisit().getBranch();
            require(
                    sourceBranch != null
                            && branch.getId().equals(sourceBranch.getId()),
                    "สาขางวดจ่ายต้องตรงกับสาขาที่ให้บริการของค่ามือต้นทาง");
        }

        if (adjustment.getAllocation() != null) {
            CommissionAllocationLedger allocation = adjustment.getAllocation();
            Long staffId = adjustment.getStaff().getId();

            boolean owner = staffId.equals(allocation.getOwnerStaff().getId());
            boolean treating = staffId.equals(allocation.getTreatingStaff().getId());

            require(owner || treating,
                    "พนักงานของรายการปรับค่าตอบแทนไม่ตรงกับ allocation ต้นทาง");
            Branch sourceBranch = owner
                    ? allocation.getCourse().getBranch()
                    : allocation.getVisitUsage().getVisit().getBranch();
            require(
                    sourceBranch != null
                            && branch.getId().equals(sourceBranch.getId()),
                    "สาขางวดจ่ายต้องตรงกับสาขาของค่าตอบแทน allocation ต้นทาง");
        }

        if (adjustment.getAllocation() == null
                && payoutCourse != null
                && adjustment.getStaff().getId().equals(
                payoutCourse.getSellerStaff().getId())) {
            Branch sourceBranch = payoutCourse.getBranch();
            require(
                    sourceBranch != null
                            && branch.getId().equals(sourceBranch.getId()),
                    "สาขางวดจ่ายต้องตรงกับสาขาขายคอร์สของ Owner");
        }

        if (adjustment.getAllocation() == null
                && adjustment.getSingleVisitFee() == null
                && adjustment.getVisit() != null
                && (payoutCourse == null
                || !adjustment.getStaff().getId().equals(
                payoutCourse.getSellerStaff().getId()))) {
            PatientVisit sourceVisit = adjustment.getVisit();
            require(
                    sourceVisit.getTreatingStaff() != null
                            && adjustment.getStaff().getId().equals(
                            sourceVisit.getTreatingStaff().getId()),
                    "พนักงานของรายการปรับค่าตอบแทนต้องตรงกับผู้รักษาใน Visit ต้นทาง");
            Branch sourceBranch = sourceVisit.getBranch();
            require(
                    sourceBranch != null
                            && branch.getId().equals(sourceBranch.getId()),
                    "สาขางวดจ่ายต้องตรงกับสาขาที่รักษาของ Visit ต้นทาง");
        }

        if (adjustment.getAllocation() == null
                && adjustment.getSingleVisitFee() == null
                && adjustment.getVisit() == null
                && payoutCourse != null) {

            require(
                    adjustment.getStaff().getId().equals(
                            payoutCourse.getSellerStaff().getId()),
                    "พนักงานของรายการปรับค่าตอบแทนต้องตรงกับ Owner ของคอร์สต้นทาง");
        }

        if (adjustment.getAllocation() == null
                && adjustment.getSingleVisitFee() == null
                && adjustment.getCourse() == null
                && adjustment.getVisit() == null
                && adjustment.getSaleTransaction() != null
                && adjustment.getSaleTransaction().getTransactionType()
                != com.sabyeguyz.clinicv2.enums.TransactionType.CoursePurchase) {

            SalesTransaction sourceSale = adjustment.getSaleTransaction();

            require(
                    sourceSale.getTransactionType()
                            == com.sabyeguyz.clinicv2.enums.TransactionType.SingleVisit,
                    "ประเภทรายการขายไม่มีต้นทางค่าตอบแทนที่รองรับ");

            PatientVisit sourceVisit = sourceSale.getVisit();

            require(sourceVisit != null,
                    "รายการขายรายครั้งต้องมี Visit ต้นทาง");

            require(
                    sourceVisit.getTreatingStaff() != null
                            && adjustment.getStaff().getId().equals(
                            sourceVisit.getTreatingStaff().getId()),
                    "พนักงานของรายการปรับค่าตอบแทนต้องตรงกับผู้รักษาใน Visit ต้นทาง");

            Branch sourceBranch = sourceVisit.getBranch();

            require(
                    sourceBranch != null
                            && branch.getId().equals(sourceBranch.getId()),
                    "สาขางวดจ่ายต้องตรงกับสาขาที่รักษาของรายการขายต้นทาง");
        }

        require(adjustment.getAdjustmentStatus() == AdjustmentStatus.Approved
                || adjustment.getAdjustmentStatus() == AdjustmentStatus.PartiallyApplied, "นำไปใช้ได้เฉพาะใบปรับยอดที่อนุมัติแล้วและยังไม่ครบ");
        StaffMonthlyPaymentService.requireMonth(month);
        require(month.compareTo(adjustment.getAppliedMonth()) >= 0 && month.compareTo(adjustment.getOriginalMonth()) > 0,
                "เดือนที่นำไปใช้ต้องไม่ก่อนเดือน " + adjustment.getAppliedMonth());
        BigDecimal drafts = applications.findByAdjustmentIdOrderByIdAsc(adjustmentId)
                .stream()
                .filter(application -> application.getApplicationStatus() == ApplicationStatus.Draft)
                .map(application -> application.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = adjustment.getRemainingAmount().subtract(drafts);
        BigDecimal wanted = amountAbs == null ? available : amountAbs;
        require(wanted.signum() > 0 && wanted.stripTrailingZeros().scale() <= 2, "ยอดที่นำไปใช้ต้องมากกว่า 0 และมีทศนิยมไม่เกิน 2 ตำแหน่ง");
        require(wanted.compareTo(available) <= 0, "ยอดที่นำไปใช้รวมกันต้องไม่เกินยอดคงเหลือของใบปรับยอด (" + available + ")");
        StaffMonthlyPayment payment = paymentService.openFor(adjustment.getStaff(), month, branch, actorId);
        require(payment.getPaymentStatus() == PaymentStatus.Open, "เพิ่มได้เฉพาะงวดที่ยังเป็น Open (งวด Paid แล้วห้ามเพิ่ม)");

        AdjustmentApplication application = new AdjustmentApplication();
        application.setAdjustment(adjustment);
        application.setMonthlyPayment(payment);
        application.setAmount(adjustment.getAmount().signum() < 0 ? wanted.negate() : wanted);
        application.setApplicationStatus(ApplicationStatus.Draft);
        application.setCreatedAt(Instant.now());
        application.setCreatedBy(actorId);

        return applications.save(application);
    }

    /** The Manager's approval of the real addition or deduction (a deduction needs the consent reference). */
    @Transactional
    public AdjustmentApplication approveApplication(
            Long applicationId,
            String reference,
            Long actorId) {
        AdjustmentApplication application = findApplication(applicationId);
        require(application.getApplicationStatus() == ApplicationStatus.Draft, "อนุมัติได้เฉพาะรายการที่เป็น Draft");

        if (application.getAmount().signum() < 0) {
            require(reference != null && !reference.isBlank(), "การหักเงินต้องระบุเลขอ้างอิงการอนุมัติ/ความยินยอม");
        }

        application.setApprovedBy(actorId);
        application.setApprovedAt(Instant.now());
        application.setApprovalReference(reference == null || reference.isBlank() ? null : reference.trim());

        return applications.save(application);
    }

    @Transactional
    public AdjustmentApplication cancelApplication(Long applicationId) {
        AdjustmentApplication application = findApplication(applicationId);
        require(application.getApplicationStatus() == ApplicationStatus.Draft, "รายการที่ Applied แล้วห้ามยกเลิก ให้สร้างใบปรับยอดใหม่");
        application.setApplicationStatus(ApplicationStatus.Cancelled);

        return applications.save(application);
    }

    private void cancelDrafts(StaffPayAdjustment adjustment) {
        for (AdjustmentApplication application : applications.findByAdjustmentIdOrderByIdAsc(adjustment.getId())) {
            if (application.getApplicationStatus() == ApplicationStatus.Draft) {
                application.setApplicationStatus(ApplicationStatus.Cancelled);
                applications.save(application);
            }
        }
    }

    private AdjustmentApplication findApplication(Long id) {
        return applications.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบรายการนำไปใช้"));
    }

    private static void require(
            boolean condition,
            String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
