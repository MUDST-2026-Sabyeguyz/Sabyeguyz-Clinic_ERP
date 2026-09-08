package com.sabyeguyz.clinic.report;

import com.sabyeguyz.clinic.appointment.AppointmentStatus;
import com.sabyeguyz.clinic.branch.Branch;
import com.sabyeguyz.clinic.branch.BranchRepository;
import com.sabyeguyz.clinic.course.CoursePurchaseStatus;
import com.sabyeguyz.clinic.patient.PatientNotFoundException;
import com.sabyeguyz.clinic.patient.PatientRepository;
import com.sabyeguyz.clinic.sale.SaleItemType;
import com.sabyeguyz.clinic.sale.SaleStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard และ Report ตาม Workflow Brief V1 §8
 *
 * <p>กติกาที่ยึดทุกที่ในไฟล์นี้: <b>รายได้คือผลรวมของรายการรับเงินหักคืนเงิน ไม่ใช่ {@code sales.total}</b>
 * ใบเสร็จที่ถูกยกเลิกยังมี total เท่าเดิมอยู่ในตาราง แต่รายการคืนเงินหักล้างจนเหลือศูนย์
 * ถ้ารายงานไหนอ่าน total มาบวกกันตรงๆ ตัวเลขจะเกินความจริงทุกครั้งที่มีการยกเลิก</p>
 */
@Service
@Transactional(readOnly = true)
public class ReportService {
    private static final String UNKNOWN_GENDER = "ไม่ระบุ";

    private final ReportQueries queries;
    private final BranchRepository branchRepository;
    private final PatientRepository patientRepository;

    public ReportService(ReportQueries queries, BranchRepository branchRepository,
                         PatientRepository patientRepository) {
        this.queries = queries;
        this.branchRepository = branchRepository;
        this.patientRepository = patientRepository;
    }

    public DashboardResponse dashboard(ReportPeriod period, String branchCode) {
        Long branchId = resolveBranchId(branchCode);
        Instant from = period.startInstant();
        Instant to = period.endInstantExclusive();

        List<ReportRows.GenderCount> genders =
                queries.uniquePatientsByGender(from, to, AppointmentStatus.attended(), branchId);
        long uniquePatients = genders.stream().mapToLong(ReportRows.GenderCount::patients).sum();

        Map<SaleItemType, BigDecimal> byCategory = categoryTotals(from, to, branchId);

        return new DashboardResponse(
                period.from(),
                period.to(),
                normalise(branchCode),
                uniquePatients,
                genders.stream()
                        .map(row -> new DashboardResponse.GenderBreakdown(labelGender(row.gender()), row.patients()))
                        .sorted((left, right) -> Long.compare(right.patients(), left.patients()))
                        .toList(),
                queries.countVisits(from, to, AppointmentStatus.attended(), branchId),
                money(queries.totalRevenue(from, to, branchId)),
                byCategory.getOrDefault(SaleItemType.COURSE, zero()),
                byCategory.getOrDefault(SaleItemType.SINGLE_VISIT, zero()),
                byCategory.getOrDefault(SaleItemType.ASSESSMENT, zero()),
                queries.countReceipts(from, to, SaleStatus.PAID, branchId),
                queries.countReceipts(from, to, SaleStatus.VOIDED, branchId));
    }

    public RevenueReportResponse revenue(ReportPeriod period, String branchCode) {
        Long branchId = resolveBranchId(branchCode);
        Instant from = period.startInstant();
        Instant to = period.endInstantExclusive();

        BigDecimal total = money(queries.totalRevenue(from, to, branchId));
        Map<SaleItemType, BigDecimal> categories = categoryTotals(from, to, branchId);

        List<RevenueReportResponse.CategoryRevenue> byCategory = new ArrayList<>();
        for (SaleItemType type : SaleItemType.values()) {
            BigDecimal amount = categories.getOrDefault(type, zero());
            byCategory.add(new RevenueReportResponse.CategoryRevenue(type, amount, share(amount, total)));
        }

        return new RevenueReportResponse(
                period.from(),
                period.to(),
                normalise(branchCode),
                total,
                byCategory,
                queries.revenueByBranch(from, to, branchId).stream()
                        .map(row -> new RevenueReportResponse.BranchRevenue(
                                row.branchCode(), row.branchName(), money(row.amount())))
                        .toList(),
                dailySeries(period, queries.revenuePerSale(from, to, branchId)));
    }

    public CourseBalanceReportResponse courseBalances(ReportPeriod period, String branchCode, Long patientId) {
        Long branchId = resolveBranchId(branchCode);
        if (patientId != null && !patientRepository.existsById(patientId)) {
            throw new PatientNotFoundException(patientId);
        }

        List<ReportRows.PurchaseRow> purchases = queries.coursePurchases(
                period.startInstant(), period.endInstantExclusive(), branchId, patientId);
        if (purchases.isEmpty()) {
            return new CourseBalanceReportResponse(period.from(), period.to(), normalise(branchCode),
                    0, 0, 0, 0, zero(), List.of());
        }

        // ดึงยอดคงเหลือและยอดที่ใช้ไปของทุกใบในคราวเดียว ไม่ยิง query ต่อหนึ่งใบ
        List<Long> ids = purchases.stream().map(ReportRows.PurchaseRow::purchaseId).toList();
        Map<Long, Long> balances = totalsById(queries.balancesOf(ids));
        Map<Long, Long> used = totalsById(queries.usedSessionsOf(ids));

        Instant now = Instant.now();
        List<CourseBalanceReportResponse.Row> rows = purchases.stream().map(purchase -> {
            int purchased = purchase.sessions() + purchase.bonusSessions();
            return new CourseBalanceReportResponse.Row(
                    purchase.purchaseId(), purchase.patientId(), purchase.hn(), purchase.patientFullName(),
                    purchase.branchCode(), purchase.courseCode(), purchase.courseName(), purchase.price(),
                    purchased,
                    Math.toIntExact(used.getOrDefault(purchase.purchaseId(), 0L)),
                    Math.toIntExact(balances.getOrDefault(purchase.purchaseId(), 0L)),
                    purchase.purchasedAt(), purchase.expiresAt(),
                    !now.isBefore(purchase.expiresAt()),
                    purchase.status());
        }).toList();

        return new CourseBalanceReportResponse(
                period.from(), period.to(), normalise(branchCode),
                rows.size(),
                rows.stream().mapToLong(CourseBalanceReportResponse.Row::purchased).sum(),
                rows.stream().mapToLong(CourseBalanceReportResponse.Row::used).sum(),
                rows.stream().mapToLong(CourseBalanceReportResponse.Row::remaining).sum(),
                // มูลค่าคอร์สที่ขายได้ ไม่นับใบที่ถูกยกเลิกไปแล้ว
                money(rows.stream()
                        .filter(row -> row.status() == CoursePurchaseStatus.ACTIVE)
                        .map(CourseBalanceReportResponse.Row::price)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)),
                rows);
    }

    public StaffSalesReportResponse staffSales(ReportPeriod period, String branchCode) {
        Long branchId = resolveBranchId(branchCode);
        List<ReportRows.StaffSaleRow> raw =
                queries.salesByStaff(period.startInstant(), period.endInstantExclusive(), branchId);

        StaffSalesReportResponse.Row unassigned = raw.stream()
                .filter(row -> row.staffId() == null)
                .findFirst()
                .map(row -> new StaffSalesReportResponse.Row(null, null, "ไม่ระบุผู้ขาย",
                        row.receipts(), money(row.amount())))
                .orElse(new StaffSalesReportResponse.Row(null, null, "ไม่ระบุผู้ขาย", 0, zero()));

        List<StaffSalesReportResponse.Row> rows = raw.stream()
                .filter(row -> row.staffId() != null)
                .map(row -> new StaffSalesReportResponse.Row(row.staffId(), row.employeeCode(),
                        row.fullName(), row.receipts(), money(row.amount())))
                .sorted((left, right) -> right.amount().compareTo(left.amount()))
                .toList();

        BigDecimal branchTotal = money(raw.stream()
                .map(ReportRows.StaffSaleRow::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        long receipts = raw.stream().mapToLong(ReportRows.StaffSaleRow::receipts).sum();

        return new StaffSalesReportResponse(period.from(), period.to(), normalise(branchCode),
                branchTotal, receipts, rows, unassigned);
    }

    /**
     * จัดกลุ่มยอดขายเป็นรายวันตามวันที่ของคลินิก และเติมวันที่ไม่มียอดให้เป็นศูนย์
     * เพื่อให้กราฟฝั่ง Frontend ไม่มีช่องโหว่
     */
    private List<RevenueReportResponse.DailyRevenue> dailySeries(ReportPeriod period,
                                                                 List<ReportRows.SaleAmount> sales) {
        Map<LocalDate, BigDecimal> buckets = new LinkedHashMap<>();
        for (LocalDate date = period.from(); !date.isAfter(period.to()); date = date.plusDays(1)) {
            buckets.put(date, BigDecimal.ZERO);
        }
        for (ReportRows.SaleAmount sale : sales) {
            LocalDate date = period.clinicDateOf(sale.soldAt());
            buckets.computeIfPresent(date, (key, running) -> running.add(sale.amount()));
        }
        return buckets.entrySet().stream()
                .map(entry -> new RevenueReportResponse.DailyRevenue(entry.getKey(), money(entry.getValue())))
                .toList();
    }

    private Map<SaleItemType, BigDecimal> categoryTotals(Instant from, Instant to, Long branchId) {
        return queries.revenueByCategory(from, to, branchId).stream()
                .collect(Collectors.toMap(ReportRows.CategoryAmount::type,
                        row -> money(row.amount()), BigDecimal::add, LinkedHashMap::new));
    }

    private static Map<Long, Long> totalsById(List<ReportRows.PurchaseTotal> totals) {
        return totals.stream().collect(Collectors.toMap(
                ReportRows.PurchaseTotal::purchaseId, ReportRows.PurchaseTotal::value));
    }

    private Long resolveBranchId(String branchCode) {
        if (branchCode == null || branchCode.isBlank()) {
            return null;
        }
        return branchRepository.findByCodeAndActiveTrue(branchCode.trim().toUpperCase())
                .map(Branch::getId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขาที่เปิดใช้งาน " + branchCode));
    }

    private static String normalise(String branchCode) {
        return branchCode == null || branchCode.isBlank() ? null : branchCode.trim().toUpperCase();
    }

    private static String labelGender(String gender) {
        return gender == null || gender.isBlank() ? UNKNOWN_GENDER : gender;
    }

    /** สัดส่วนของยอดต่อรายได้รวม ทศนิยม 4 ตำแหน่ง คืน 0 เมื่อไม่มีรายได้เลย */
    private static BigDecimal share(BigDecimal amount, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return amount.divide(total, 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
