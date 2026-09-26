package com.sabyeguyz.clinicv2.report;

import com.sabyeguyz.clinicv2.appointment.Appointment;
import com.sabyeguyz.clinicv2.appointment.AppointmentRepository;
import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.appointment.PatientVisitRepository;
import com.sabyeguyz.clinicv2.branch.BranchRepository;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import com.sabyeguyz.clinicv2.enums.TransactionType;
import com.sabyeguyz.clinicv2.enums.VisitStatus;
import com.sabyeguyz.clinicv2.sale.PaymentTransaction;
import com.sabyeguyz.clinicv2.sale.PaymentTransactionRepository;
import com.sabyeguyz.clinicv2.sale.SalesTransaction;
import com.sabyeguyz.clinicv2.sale.SalesTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/**
 * Matches frontend/src/api/client.ts's reportApi (/api/dashboard/summary,
 * /api/reports/*). All figures here are computed by fetching the filtered
 * rows and aggregating in Java rather than SQL aggregates, since demo/dev
 * data volumes are small - revisit with real SQL aggregation before this
 * runs against production-scale data.
 *
 * <p>receiptsVoided is approximated from Sales_transaction.transaction_status
 * (per-transaction, not per-Receipt) since Sales_transaction has no
 * receipt_id to group by - see SaleController's Javadoc for the same
 * underlying schema gap.</p>
 */
@RestController
public class ReportController {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Bangkok");

    private final PatientVisitRepository visits;
    private final PaymentTransactionRepository payments;
    private final com.sabyeguyz.clinicv2.sale.ReceiptRepository receipts;
    private final SalesTransactionRepository transactions;
    private final AppointmentRepository appointments;
    private final CourseRepository courses;
    private final BranchRepository branches;

    public ReportController(PatientVisitRepository visits, PaymentTransactionRepository payments,
                             com.sabyeguyz.clinicv2.sale.ReceiptRepository receipts,
                             SalesTransactionRepository transactions, AppointmentRepository appointments,
                             CourseRepository courses, BranchRepository branches) {
        this.visits = visits;
        this.payments = payments;
        this.receipts = receipts;
        this.transactions = transactions;
        this.appointments = appointments;
        this.courses = courses;
        this.branches = branches;
    }

    public record GenderCount(String gender, long patients) {}
    public record DashboardResponse(long uniquePatients, List<GenderCount> patientsByGender, long visits,
                                     BigDecimal totalRevenue, BigDecimal courseRevenue, BigDecimal singleVisitRevenue,
                                     BigDecimal assessmentRevenue, long receiptsIssued, long receiptsVoided) {}

    public record StatusCount(String status, long appointments) {}
    public record AppointmentReport(List<StatusCount> byStatus, long appointments, long visits, BigDecimal noShowRate) {}

    public record CategoryAmount(String type, BigDecimal amount, BigDecimal share) {}
    public record DailyCategoryAmount(LocalDate date, String type, BigDecimal amount) {}
    public record RevenueReport(BigDecimal totalRevenue, List<CategoryAmount> byCategory, List<DailyCategoryAmount> dailyByCategory) {}

    public record BalanceRow(String hn, String patientFullName, String branchCode, String courseName, int purchased,
                              int used, int remaining, Instant purchasedAt, Instant expiresAt, boolean expired) {}
    public record CourseBalanceReport(int purchases, List<BalanceRow> rows) {}

    public record StaffSaleRow(String fullName, long receipts, BigDecimal amount) {}
    public record StaffSalesReport(List<StaffSaleRow> rows, StaffSaleRow unassigned) {}

    @GetMapping("/api/dashboard/summary")
    public DashboardResponse dashboard(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                        @RequestParam(required = false) String branchCode) {
        Long branchId = resolveBranchId(branchCode);
        Instant fromInstant = from.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();

        List<PatientVisit> attendedVisits = visits.findByVisitDateBetween(fromInstant, toInstant, branchId).stream()
                .filter(v -> v.getVisitStatus() == VisitStatus.Arrived || v.getVisitStatus() == VisitStatus.InService
                        || v.getVisitStatus() == VisitStatus.Completed)
                .toList();
        long uniquePatients = attendedVisits.stream().map(v -> v.getPatient().getId()).distinct().count();
        Map<String, Long> genderCounts = new LinkedHashMap<>();
        attendedVisits.stream().map(v -> v.getPatient().getId()).distinct().forEach(pid -> {
            String gender = attendedVisits.stream().filter(v -> v.getPatient().getId().equals(pid)).findFirst()
                    .map(v -> v.getPatient().getGender() == null ? "Other" : v.getPatient().getGender().name())
                    .orElse("Other");
            genderCounts.merge(gender, 1L, Long::sum);
        });

        List<PaymentTransaction> paid = payments.findByPaymentDateBetween(fromInstant, toInstant, branchId);
        BigDecimal totalRevenue = sum(paid, PaymentTransaction::getAmount);
        BigDecimal courseRevenue = sum(paid.stream().filter(p -> p.getTransaction().getTransactionType() == TransactionType.CoursePurchase).toList(), PaymentTransaction::getAmount);
        BigDecimal singleVisitRevenue = sum(paid.stream().filter(p -> p.getTransaction().getTransactionType() == TransactionType.SingleVisit).toList(), PaymentTransaction::getAmount);
        BigDecimal assessmentRevenue = sum(paid.stream().filter(p -> p.getTransaction().getTransactionType() == TransactionType.Assessment).toList(), PaymentTransaction::getAmount);

        long receiptsIssued = receipts.findBySaleDateBetween(from, to, branchId).size();
        long receiptsVoided = transactions.findByTransactionDateBetween(fromInstant, toInstant, branchId).stream()
                .filter(t -> !t.isTransactionStatus()).count();

        return new DashboardResponse(uniquePatients,
                genderCounts.entrySet().stream().map(e -> new GenderCount(e.getKey(), e.getValue())).toList(),
                attendedVisits.size(), totalRevenue, courseRevenue, singleVisitRevenue, assessmentRevenue,
                receiptsIssued, receiptsVoided);
    }

    @GetMapping("/api/reports/appointments")
    public AppointmentReport appointments(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                           @RequestParam(required = false) String branchCode) {
        Long branchId = resolveBranchId(branchCode);
        List<Appointment> rows = appointments.findByAppointmentDateBetween(from, to, branchId);
        Map<AppointmentStatus, Long> byStatus = new EnumMap<>(AppointmentStatus.class);
        rows.forEach(a -> byStatus.merge(a.getAppointmentStatus(), 1L, Long::sum));
        long attended = rows.stream().filter(a -> AppointmentStatus.attended().contains(a.getAppointmentStatus())).count();
        long noShow = byStatus.getOrDefault(AppointmentStatus.NoShow, 0L);
        BigDecimal noShowRate = rows.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(noShow).divide(BigDecimal.valueOf(rows.size()), 4, java.math.RoundingMode.HALF_UP);
        return new AppointmentReport(byStatus.entrySet().stream().map(e -> new StatusCount(e.getKey().name(), e.getValue())).toList(),
                rows.size(), attended, noShowRate);
    }

    @GetMapping("/api/reports/revenue")
    public RevenueReport revenue(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                  @RequestParam(required = false) String branchCode) {
        Long branchId = resolveBranchId(branchCode);
        Instant fromInstant = from.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();
        List<PaymentTransaction> paid = payments.findByPaymentDateBetween(fromInstant, toInstant, branchId);
        BigDecimal total = sum(paid, PaymentTransaction::getAmount);

        Map<TransactionType, BigDecimal> byType = new EnumMap<>(TransactionType.class);
        Map<String, BigDecimal> daily = new LinkedHashMap<>();
        for (PaymentTransaction p : paid) {
            TransactionType type = p.getTransaction().getTransactionType();
            byType.merge(type, p.getAmount(), BigDecimal::add);
            String key = LocalDate.ofInstant(p.getPaymentDate(), CLINIC_ZONE) + "|" + type;
            daily.merge(key, p.getAmount(), BigDecimal::add);
        }
        List<CategoryAmount> byCategory = byType.entrySet().stream()
                .map(e -> new CategoryAmount(e.getKey().name(), e.getValue(),
                        total.signum() == 0 ? BigDecimal.ZERO : e.getValue().divide(total, 4, java.math.RoundingMode.HALF_UP)))
                .toList();
        List<DailyCategoryAmount> dailyList = daily.entrySet().stream().map(e -> {
            String[] parts = e.getKey().split("\\|");
            return new DailyCategoryAmount(LocalDate.parse(parts[0]), parts[1], e.getValue());
        }).toList();
        return new RevenueReport(total, byCategory, dailyList);
    }

    @GetMapping("/api/reports/course-balances")
    public CourseBalanceReport courseBalances(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                               @RequestParam(required = false) String branchCode,
                                               @RequestParam(required = false) Long patientId) {
        Long branchId = resolveBranchId(branchCode);
        List<Course> rows = courses.findByReceiptSaleDateBetween(from, to).stream()
                .filter(c -> branchId == null || c.getReceipt().getBranch().getId().equals(branchId))
                .filter(c -> patientId == null || c.getReceipt().getPatient().getId().equals(patientId))
                .toList();
        List<BalanceRow> balanceRows = rows.stream().map(c -> {
            int purchased = c.getTotalVisits() + (c.getBonusVisits() == null ? 0 : c.getBonusVisits());
            var patient = c.getReceipt().getPatient();
            return new BalanceRow(patient.getPatientHn(), patient.getPatientFname() + " " + patient.getPatientLname(),
                    c.getReceipt().getBranch().getBranchCode(), c.getCourseName(), purchased, c.getVisitsUsed(),
                    c.getRemainingVisits(), c.getCourseCreateAt(),
                    c.getExpiryDate().atStartOfDay(CLINIC_ZONE).toInstant(),
                    c.getExpiryDate().isBefore(LocalDate.now()));
        }).toList();
        return new CourseBalanceReport(balanceRows.size(), balanceRows);
    }

    @GetMapping("/api/reports/staff-sales")
    public StaffSalesReport staffSales(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                        @RequestParam(required = false) String branchCode) {
        Long branchId = resolveBranchId(branchCode);
        Instant fromInstant = from.atStartOfDay(CLINIC_ZONE).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();
        List<SalesTransaction> rows = transactions.findByTransactionDateBetween(fromInstant, toInstant, branchId).stream()
                .filter(SalesTransaction::isTransactionStatus).toList();

        Map<Long, List<SalesTransaction>> bySeller = new LinkedHashMap<>();
        List<SalesTransaction> unassigned = new ArrayList<>();
        for (SalesTransaction t : rows) {
            if (t.getSalespersonStaff() == null) unassigned.add(t);
            else bySeller.computeIfAbsent(t.getSalespersonStaff().getId(), k -> new ArrayList<>()).add(t);
        }
        List<StaffSaleRow> staffRows = bySeller.values().stream().map(group -> new StaffSaleRow(
                group.get(0).getSalespersonStaff().getStaffFname() + " " + group.get(0).getSalespersonStaff().getStaffLname(),
                group.size(), sum(group, SalesTransaction::getAmount))).toList();
        StaffSaleRow unassignedRow = unassigned.isEmpty() ? null
                : new StaffSaleRow(null, unassigned.size(), sum(unassigned, SalesTransaction::getAmount));
        return new StaffSalesReport(staffRows, unassignedRow);
    }

    private Long resolveBranchId(String branchCode) {
        if (branchCode == null || branchCode.isBlank()) return null;
        return branches.findByBranchCode(branchCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + branchCode)).getId();
    }

    private static <T> BigDecimal sum(List<T> items, java.util.function.Function<T, BigDecimal> extractor) {
        return items.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
