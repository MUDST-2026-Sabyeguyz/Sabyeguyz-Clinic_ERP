package com.sabyeguyz.clinic.course;

import com.sabyeguyz.clinic.appointment.Appointment;
import com.sabyeguyz.clinic.appointment.AppointmentNotFoundException;
import com.sabyeguyz.clinic.appointment.AppointmentRepository;
import com.sabyeguyz.clinic.branch.Branch;
import com.sabyeguyz.clinic.branch.BranchRepository;
import com.sabyeguyz.clinic.catalog.Course;
import com.sabyeguyz.clinic.catalog.CourseNotFoundException;
import com.sabyeguyz.clinic.catalog.CourseRepository;
import com.sabyeguyz.clinic.patient.Patient;
import com.sabyeguyz.clinic.patient.PatientNotFoundException;
import com.sabyeguyz.clinic.patient.PatientRepository;
import com.sabyeguyz.clinic.staff.StaffRepository;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sabyeguyz.clinic.staff.Staff;
import com.sabyeguyz.clinic.commission.CommissionAllocationLedger;
import com.sabyeguyz.clinic.commission.CommissionAllocationLedgerRepository;
import com.sabyeguyz.clinic.commission.TreatmentFeeRuleRepository;

/**
 * ดูแลยอดคงเหลือของคอร์สทั้งหมดผ่านบัญชีเดินสะพัด ({@link CourseMovement})
 * ไม่มีที่ไหนในระบบที่แก้ตัวเลข remaining โดยตรง ทุกการเปลี่ยนแปลงเป็นการเพิ่มรายการใหม่
 */
@Service
public class CourseLedgerService {
    private final CoursePurchaseRepository purchaseRepository;
    private final CourseMovementRepository movementRepository;
    private final PatientRepository patientRepository;
    private final CourseRepository courseRepository;
    private final BranchRepository branchRepository;
    private final AppointmentRepository appointmentRepository;
    private final StaffRepository staffRepository;
    private final TreatmentFeeRuleRepository treatmentFees;
    private final CommissionAllocationLedgerRepository ledger;

    public CourseLedgerService(CoursePurchaseRepository purchaseRepository,
                               CourseMovementRepository movementRepository,
                               PatientRepository patientRepository,
                               CourseRepository courseRepository,
                               BranchRepository branchRepository,
                               AppointmentRepository appointmentRepository, StaffRepository staffRepository, TreatmentFeeRuleRepository treatmentFees, CommissionAllocationLedgerRepository ledger) {
        this.purchaseRepository = purchaseRepository;
        this.movementRepository = movementRepository;
        this.patientRepository = patientRepository;
        this.courseRepository = courseRepository;
        this.branchRepository = branchRepository;
        this.appointmentRepository = appointmentRepository;
        this.staffRepository = staffRepository;
        this.treatmentFees=treatmentFees; this.ledger=ledger;
    }

    @Transactional
    public CoursePurchaseResponse purchase(CoursePurchaseRequest request) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new PatientNotFoundException(request.patientId()));
        if (!patient.isActive()) {
            throw new IllegalArgumentException("ผู้ป่วยถูกปิดการใช้งาน");
        }
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(CourseNotFoundException::new);
        if (!course.isActive()) {
            throw new IllegalArgumentException("คอร์สถูกปิดการใช้งาน");
        }
        Branch branch = branchRepository.findByBranchCodeAndActiveTrue(request.branchCode().trim())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขาที่เปิดใช้งาน"));

        CoursePurchase purchase = openPurchase(patient, course, branch, Instant.now());
        if (request.sellerEmployeeId()!=null) purchase.assignSeller(staffRepository.findById(request.sellerEmployeeId()).orElseThrow(()->new IllegalArgumentException("ไม่พบพนักงานผู้ขาย")));
        purchaseRepository.save(purchase);
        return describe(purchase, Instant.now());
    }

    /**
     * เปิดใบซื้อคอร์สและเครดิตสิทธิ์เข้าบัญชีทันที ให้ซื้อแล้วใช้วันเดียวกันได้
     * แยกออกมาเพื่อให้การขายผ่านใบเสร็จเรียกใช้ได้ โดยที่ผู้เรียกตรวจ patient/branch มาแล้ว
     */
    @Transactional
    public CoursePurchase openPurchase(Patient patient, Course course, Branch branch, Instant purchasedAt) {
        if (!course.isActive()) {
            throw new IllegalArgumentException("คอร์สถูกปิดการใช้งาน");
        }
        CoursePurchase purchase = purchaseRepository.save(
                new CoursePurchase(patient, course, branch, purchasedAt));
        movementRepository.save(CourseMovement.purchase(purchase));
        return purchase;
    }

    /**
     * ตัดสิทธิ์ 1 ครั้ง ล็อกใบซื้อไว้ก่อนอ่านยอด เพื่อกันสอง request ตัดครั้งสุดท้ายพร้อมกัน
     * ซื้อและใช้ในวันเดียวกันทำได้ เพราะสิทธิ์ถูกบันทึกตั้งแต่ตอนซื้อแล้ว
     */
    @Transactional
    public CoursePurchaseResponse use(CourseUsageRequest request) {
        CoursePurchase purchase = lock(request.coursePurchaseId());
        Instant now = Instant.now();

        if (purchase.getStatus() == CoursePurchaseStatus.CANCELLED) {
            throw new CourseBalanceException("คอร์สนี้ถูกยกเลิกแล้ว ใช้สิทธิ์ไม่ได้");
        }
        if (purchase.isExpiredAt(now)) {
            throw new CourseBalanceException("คอร์สหมดอายุแล้ว ใช้สิทธิ์ไม่ได้");
        }

        int remaining = movementRepository.balanceOf(purchase.getId());
        if (remaining <= 0) {
            throw new CourseBalanceException("คอร์สนี้ใช้สิทธิ์ครบแล้ว");
        }

        Appointment appointment = resolveAppointment(request.appointmentId(), purchase);
        movementRepository.save(CourseMovement.usage(purchase, appointment, request.note()));
        if (purchase.getCommissionAllocationPerVisit()!=null) allocationLedger(request, purchase, appointment, now);

        return describe(purchase, now);
    }

    private void allocationLedger(CourseUsageRequest request, CoursePurchase purchase, Appointment appointment, Instant now) {
        Staff treating = request.treatingStaffId()==null?null:staffRepository.findById(request.treatingStaffId()).orElseThrow(()->new IllegalArgumentException("ไม่พบผู้รักษา"));
        BigDecimal gross=purchase.getCommissionAllocationPerVisit(); BigDecimal fee=BigDecimal.ZERO;
        if(treating!=null && purchase.getCaseOwner()!=null && !treating.getId().equals(purchase.getCaseOwner().getId())) fee=treatmentFees.findCurrent(treating.getId(),now.atZone(java.time.ZoneId.of("Asia/Bangkok")).toLocalDate()).map(r->r.calculate(purchase)).orElse(BigDecimal.ZERO).min(gross);
        ledger.save(new CommissionAllocationLedger(purchase,gross,fee,gross.subtract(fee),purchase.getCaseOwner(),treating,now.atZone(java.time.ZoneId.of("Asia/Bangkok")).toLocalDate()));
    }

    /** รายการใช้สิทธิ์ทั้งหมดสำหรับหน้าธุรกรรม พร้อมข้อมูลการกลับรายการ */
    @Transactional(readOnly = true)
    public List<CourseUsageTransactionResponse> listUsageTransactions() {
        List<CourseMovement> usages = movementRepository.findByTypeOrderByIdDesc(CourseMovementType.USAGE);
        if (usages.isEmpty()) return List.of();
        Map<Long, CourseMovement> reversals = movementRepository.findByReversesMovementIdIn(
                usages.stream().map(CourseMovement::getId).toList()).stream().collect(Collectors.toMap(
                movement -> movement.getReversesMovement().getId(), Function.identity()));
        return usages.stream().map(usage -> CourseUsageTransactionResponse.from(
                usage, reversals.get(usage.getId()))).toList();
    }

    /** กลับรายการการตัดสิทธิ์ที่บันทึกผิด — รายการเดิมยังอยู่ในประวัติเสมอ */
    @Transactional
    public CoursePurchaseResponse reverseUsage(long movementId, CourseReasonRequest request) {
        CourseMovement original = movementRepository.findById(movementId)
                .orElseThrow(() -> new CourseBalanceException("ไม่พบรายการเดินบัญชี id " + movementId));
        if (original.getType() != CourseMovementType.USAGE) {
            throw new CourseBalanceException("กลับรายการได้เฉพาะรายการใช้สิทธิ์เท่านั้น");
        }
        // ล็อกใบซื้อก่อน แล้วค่อยตรวจซ้ำ เพื่อกันสอง request กลับรายการเดียวกันพร้อมกัน
        CoursePurchase purchase = lock(original.getCoursePurchase().getId());
        if (movementRepository.existsByReversesMovementId(movementId)) {
            throw new CourseBalanceException("รายการนี้ถูกกลับรายการไปแล้ว");
        }

        movementRepository.save(CourseMovement.reversalOf(original, request.reason()));
        return describe(purchase, Instant.now());
    }

    /** ยกเลิกใบซื้อทั้งใบ: ล้างสิทธิ์คงเหลือเป็นศูนย์ ครั้งที่ใช้ไปแล้วยังคงอยู่ในประวัติ */
    @Transactional
    public CoursePurchaseResponse cancel(long purchaseId, CourseReasonRequest request) {
        CoursePurchase purchase = lock(purchaseId);
        if (purchase.getStatus() == CoursePurchaseStatus.CANCELLED) {
            throw new CourseBalanceException("คอร์สนี้ถูกยกเลิกไปแล้ว");
        }

        int remaining = movementRepository.balanceOf(purchaseId);
        if (remaining != 0) {
            movementRepository.save(CourseMovement.cancellation(purchase, remaining, request.reason()));
        }
        purchase.cancel();

        return describe(purchase, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<CoursePurchaseResponse> listByPatient(long patientId) {
        if (!patientRepository.existsById(patientId)) {
            throw new PatientNotFoundException(patientId);
        }
        Instant now = Instant.now();
        return purchaseRepository.findByPatient(patientId).stream()
                .map(purchase -> describe(purchase, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseLedgerResponse ledger(long purchaseId) {
        CoursePurchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new CoursePurchaseNotFoundException(purchaseId));

        List<CourseMovementResponse> movements = new ArrayList<>();
        int running = 0;
        for (CourseMovement movement : movementRepository.findByCoursePurchaseIdOrderByIdAsc(purchaseId)) {
            running += movement.getQuantity();
            movements.add(CourseMovementResponse.from(movement, running));
        }
        return new CourseLedgerResponse(describe(purchase, Instant.now()), movements, ledger.findByCoursePurchaseIdOrderByVisitDateAsc(purchaseId));
    }

    private CoursePurchase lock(long purchaseId) {
        return purchaseRepository.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> new CoursePurchaseNotFoundException(purchaseId));
    }

    private Appointment resolveAppointment(Long appointmentId, CoursePurchase purchase) {
        if (appointmentId == null) {
            return null;
        }
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
        if (!appointment.getPatient().getId().equals(purchase.getPatient().getId())) {
            throw new IllegalArgumentException("นัดหมายนี้ไม่ใช่ของผู้ป่วยเจ้าของคอร์ส");
        }
        return appointment;
    }

    private CoursePurchaseResponse describe(CoursePurchase purchase, Instant now) {
        return CoursePurchaseResponse.from(purchase,
                movementRepository.usedSessionsOf(purchase.getId()),
                movementRepository.balanceOf(purchase.getId()),
                now);
    }
}
