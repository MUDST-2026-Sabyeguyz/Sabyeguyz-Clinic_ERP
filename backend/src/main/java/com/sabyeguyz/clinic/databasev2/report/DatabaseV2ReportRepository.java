package com.sabyeguyz.clinic.databasev2.report;

import com.sabyeguyz.clinic.appointment.AppointmentStatus;
import com.sabyeguyz.clinic.course.CoursePurchaseStatus;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2NotFoundException;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2ConflictException;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2BusinessDates;
import com.sabyeguyz.clinic.report.AppointmentReportResponse;
import com.sabyeguyz.clinic.report.CourseBalanceReportResponse;
import com.sabyeguyz.clinic.report.DashboardResponse;
import com.sabyeguyz.clinic.report.ReportPeriod;
import com.sabyeguyz.clinic.report.RevenueReportResponse;
import com.sabyeguyz.clinic.report.StaffSalesReportResponse;
import com.sabyeguyz.clinic.sale.SaleItemType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-v2")
public class DatabaseV2ReportRepository {

    private final JdbcTemplate jdbc;
    private static final ZoneId BANGKOK_ZONE = ZoneId.of("Asia/Bangkok");

    public DatabaseV2ReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record PaidCategory(Long receiptId, BigDecimal paid, BigDecimal billDiscount,
                                long categoryCount, BigDecimal baseTotal, String type, BigDecimal categoryBase) {}

    /** Attribute cash only where the category is known; never allocate a mixed-category bill discount. */
    private Map<String, BigDecimal> paidCategoryTotals(ReportPeriod period, Long branchId) {
        String sql = """
                with paid_receipts as (
                    select case when st.receipt_link_status = 'VERIFIED' and st.transaction_status = true
                                then coalesce(st.receipt_id, c.receipt_id) end as receipt_id,
                           sum(pt.amount) as paid
                    from payment_transaction pt
                    join sales_transaction st on st.transaction_id = pt.transaction_id
                    left join course c on c.course_id = st.course_id
                    where pt.payment_status = true
                      and cast(pt.payment_date as date) >= ? and cast(pt.payment_date as date) <= ?
                """ + (branchId != null ? " and pt.branch_id = ?\n" : "\n") + """
                    group by case when st.receipt_link_status = 'VERIFIED' and st.transaction_status = true
                                  then coalesce(st.receipt_id, c.receipt_id) end
                ), category_bases as (
                    select coalesce(st.receipt_id, c.receipt_id) as receipt_id,
                           cast(st.transaction_type as varchar) as tx_type, sum(st.amount) as base,
                           bool_and(st.receipt_link_status = 'VERIFIED') as links_verified
                    from sales_transaction st
                    left join course c on c.course_id = st.course_id
                    join paid_receipts p on p.receipt_id = coalesce(st.receipt_id, c.receipt_id)
                    where st.transaction_status = true
                    group by coalesce(st.receipt_id, c.receipt_id), st.transaction_type
                ), receipt_categories as (
                    select receipt_id, count(*) as category_count, sum(base) as base_total,
                           bool_and(links_verified) as links_verified
                    from category_bases group by receipt_id
                )
                select p.receipt_id, p.paid, r.bill_discount_amount, rc.category_count, rc.base_total,
                       case when rc.links_verified then cb.tx_type end as tx_type, cb.base as category_base
                from paid_receipts p
                left join receipt r on r.receipt_id = p.receipt_id
                left join receipt_categories rc on rc.receipt_id = p.receipt_id
                left join category_bases cb on cb.receipt_id = p.receipt_id
                """;
        Object[] params = branchId != null
                ? new Object[]{period.from(), period.to(), branchId}
                : new Object[]{period.from(), period.to()};
        List<PaidCategory> rows = jdbc.query(sql, (rs, row) -> new PaidCategory(
                rs.getObject("receipt_id", Long.class), rs.getBigDecimal("paid"), rs.getBigDecimal("bill_discount_amount"),
                rs.getLong("category_count"), rs.getBigDecimal("base_total"), rs.getString("tx_type"),
                rs.getBigDecimal("category_base")), params);
        Map<String, BigDecimal> totals = new HashMap<>();
        for (PaidCategory row : rows) {
            if (row.receiptId() == null || row.type() == null || row.billDiscount() == null) {
                throw new DatabaseV2ConflictException("REVIEW_REQUIRED: ต้องตรวจสอบการผูกใบเสร็จก่อนสรุปรายได้ตามประเภท");
            }
            BigDecimal amount;
            if (row.categoryCount() == 1) {
                // All items have the same category: every received baht belongs to it, without per-item allocation.
                amount = row.paid();
            } else if (row.billDiscount().signum() == 0 && row.paid().compareTo(row.baseTotal()) == 0) {
                amount = row.categoryBase();
            } else {
                throw new DatabaseV2ConflictException("REVIEW_REQUIRED: รายได้หลายประเภทในใบเดียวต้องยืนยันการจัดสรรส่วนลดหรือยอดชำระก่อน");
            }
            totals.merge(row.type(), amount, BigDecimal::add);
        }
        return totals;
    }

    public Long resolveBranchId(String branchCode) {
        if (branchCode == null || branchCode.isBlank()) return null;
        List<Long> ids = jdbc.queryForList(
                "select branch_id from branch where branch_code = ? and branch_status = true",
                Long.class,
                branchCode.trim().toUpperCase()
        );
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("ไม่พบสาขาหรือสาขาปิดใช้งาน " + branchCode);
        }
        return ids.get(0);
    }

    private static String normalise(String branchCode) {
        return branchCode == null || branchCode.isBlank() ? null : branchCode.trim().toUpperCase();
    }

    public DashboardResponse dashboard(ReportPeriod period, String branchCode) {
        Long branchId = resolveBranchId(branchCode);
        LocalDate from = period.from();
        LocalDate to = period.to();
        String normBranch = normalise(branchCode);

        // 1. Unique patients & Gender breakdown
        String gSql = """
                select coalesce(cast(p.gender as varchar), 'ไม่ระบุ') as gender, count(distinct p.patient_id) as patients
                from (
                    select a.patient_id, a.branch_id, a.appointment_date as op_date
                    from appointment a
                    where a.appointment_status in ('Arrived', 'InService', 'Completed')
                    union
                    select pv.patient_id, pv.branch_id, cast(pv.visit_date as date) as op_date
                    from patient_visit pv
                    where pv.visit_status in ('Arrived', 'InService', 'Completed')
                ) v
                join patient p on p.patient_id = v.patient_id
                where v.op_date >= ? and v.op_date <= ?
                """ + (branchId != null ? " and v.branch_id = ?\n" : "\n") + """
                group by p.gender
                order by patients desc
                """;

        List<DashboardResponse.GenderBreakdown> genderList = branchId != null
                ? jdbc.query(gSql, (rs, rowNum) -> new DashboardResponse.GenderBreakdown(rs.getString("gender"), rs.getLong("patients")), from, to, branchId)
                : jdbc.query(gSql, (rs, rowNum) -> new DashboardResponse.GenderBreakdown(rs.getString("gender"), rs.getLong("patients")), from, to);

        long uniquePatients = genderList.stream().mapToLong(DashboardResponse.GenderBreakdown::patients).sum();

        // 2. Visits count
        String vSql = """
                select count(*) from (
                    select pv.visit_id
                    from patient_visit pv
                    where cast(pv.visit_date as date) >= ? and cast(pv.visit_date as date) <= ?
                      and pv.visit_status in ('Arrived', 'InService', 'Completed')
                """ + (branchId != null ? " and pv.branch_id = ?\n" : "\n") + """
                    union
                    select a.appointment_id
                    from appointment a
                    where a.appointment_date >= ? and a.appointment_date <= ?
                      and a.appointment_status in ('Arrived', 'InService', 'Completed')
                """ + (branchId != null ? " and a.branch_id = ?\n" : "\n") + """
                      and not exists (select 1 from patient_visit pv2 where pv2.appointment_id = a.appointment_id)
                ) combined_visits
                """;
        Long visits = branchId != null
                ? jdbc.queryForObject(vSql, Long.class, from, to, branchId, from, to, branchId)
                : jdbc.queryForObject(vSql, Long.class, from, to, from, to);
        if (visits == null) visits = 0L;

        // 3. Revenue & category totals
        String revSql = """
                select coalesce(sum(pt.amount), 0)
                from payment_transaction pt
                where pt.payment_status = true
                  and cast(pt.payment_date as date) >= ? and cast(pt.payment_date as date) <= ?
                """ + (branchId != null ? " and pt.branch_id = ?" : "");
        BigDecimal totalRevenue = branchId != null
                ? jdbc.queryForObject(revSql, BigDecimal.class, from, to, branchId)
                : jdbc.queryForObject(revSql, BigDecimal.class, from, to);
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        Map<String, BigDecimal> catMap = paidCategoryTotals(period, branchId);

        BigDecimal courseRev = catMap.getOrDefault("CoursePurchase", BigDecimal.ZERO);
        BigDecimal singleRev = catMap.getOrDefault("SingleVisit", BigDecimal.ZERO);
        BigDecimal assessRev = catMap.getOrDefault("Assessment", BigDecimal.ZERO);

        // 4. Receipts issued & voided
        String rIssuedSql = """
                select count(*) from receipt r
                where r.sale_date >= ? and r.sale_date <= ?
                """ + (branchId != null ? " and r.branch_id = ?" : "");
        Long receiptsIssued = branchId != null
                ? jdbc.queryForObject(rIssuedSql, Long.class, from, to, branchId)
                : jdbc.queryForObject(rIssuedSql, Long.class, from, to);
        if (receiptsIssued == null) receiptsIssued = 0L;

        String rVoidedSql = """
                select count(distinct coalesce(st.receipt_id, c.receipt_id))
                from transaction_cancellation tc
                join sales_transaction st on st.transaction_id = tc.transaction_id
                left join course c on c.course_id = st.course_id
                where cast(tc.cancelled_at as date) >= ? and cast(tc.cancelled_at as date) <= ?
                  and st.receipt_link_status = 'VERIFIED'
                  and (st.receipt_id is not null or c.receipt_id is not null)
                """ + (branchId != null ? " and st.branch_id = ?" : "");
        Long receiptsVoided = branchId != null
                ? jdbc.queryForObject(rVoidedSql, Long.class, from, to, branchId)
                : jdbc.queryForObject(rVoidedSql, Long.class, from, to);
        if (receiptsVoided == null) receiptsVoided = 0L;

        return new DashboardResponse(
                from, to, normBranch, uniquePatients, genderList, visits, totalRevenue,
                courseRev, singleRev, assessRev, receiptsIssued, receiptsVoided
        );
    }

    public AppointmentReportResponse appointments(ReportPeriod period, String branchCode) {
        Long branchId = resolveBranchId(branchCode);
        LocalDate from = period.from();
        LocalDate to = period.to();
        String normBranch = normalise(branchCode);

        String base = " from appointment a where a.appointment_date >= ? and a.appointment_date <= ?"
                + (branchId != null ? " and a.branch_id = ?" : "");
        Object[] params = branchId != null ? new Object[]{from, to, branchId} : new Object[]{from, to};

        Long total = jdbc.queryForObject("select count(*)" + base, Long.class, params);
        if (total == null) total = 0L;

        Long visits = jdbc.queryForObject("select count(*)" + base + " and a.appointment_status in ('Arrived', 'InService', 'Completed')", Long.class, params);
        if (visits == null) visits = 0L;

        Long noShows = jdbc.queryForObject("select count(*)" + base + " and a.appointment_status = 'NoShow'", Long.class, params);
        if (noShows == null) noShows = 0L;

        long due = visits + noShows;
        BigDecimal noShowRate = due > 0
                ? BigDecimal.valueOf(noShows).divide(BigDecimal.valueOf(due), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // By status
        String statusSql = "select cast(a.appointment_status as varchar) as status, count(*) as count" + base + " group by a.appointment_status";
        List<AppointmentReportResponse.StatusCount> byStatus = jdbc.query(statusSql, (rs, rowNum) -> {
            String s = rs.getString("status");
            AppointmentStatus statusEnum = mapAppointmentStatus(s);
            return new AppointmentReportResponse.StatusCount(statusEnum, rs.getLong("count"));
        }, params);

        byStatus.sort((a, b) -> a.status().compareTo(b.status()));

        // By therapist
        String therSql = """
                select s.staff_id, s.staff_code, concat(s.staff_fname, ' ', s.staff_lname) as full_name,
                       count(*) as total_appts,
                       count(case when a.appointment_status in ('Arrived', 'InService', 'Completed') then 1 end) as attended_visits
                from appointment a
                join staff s on s.staff_id = a.staff_id
                where a.appointment_date >= ? and a.appointment_date <= ?
                """ + (branchId != null ? " and a.branch_id = ?\n" : "\n") + """
                group by s.staff_id, s.staff_code, s.staff_fname, s.staff_lname
                order by total_appts desc
                """;
        List<AppointmentReportResponse.TherapistLoad> byTherapist = jdbc.query(therSql, (rs, rowNum) -> new AppointmentReportResponse.TherapistLoad(
                rs.getLong("staff_id"),
                rs.getString("staff_code"),
                rs.getString("full_name"),
                rs.getLong("total_appts"),
                rs.getLong("attended_visits")
        ), params);

        // Daily
        String dailySql = """
                select a.appointment_date as appt_date,
                       count(*) as total_appts,
                       count(case when a.appointment_status in ('Arrived', 'InService', 'Completed') then 1 end) as attended_visits
                from appointment a
                where a.appointment_date >= ? and a.appointment_date <= ?
                """ + (branchId != null ? " and a.branch_id = ?\n" : "\n") + """
                group by a.appointment_date
                order by appt_date asc
                """;
        Map<LocalDate, long[]> dailyMap = new HashMap<>();
        jdbc.query(dailySql, (rs) -> {
            LocalDate dt = rs.getObject("appt_date", LocalDate.class);
            dailyMap.put(dt, new long[]{rs.getLong("total_appts"), rs.getLong("attended_visits")});
        }, params);

        List<AppointmentReportResponse.DailyCount> daily = new ArrayList<>();
        LocalDate curr = from;
        while (!curr.isAfter(to)) {
            long[] counts = dailyMap.getOrDefault(curr, new long[]{0L, 0L});
            daily.add(new AppointmentReportResponse.DailyCount(curr, counts[0], counts[1]));
            curr = curr.plusDays(1);
        }

        return new AppointmentReportResponse(from, to, normBranch, total, visits, noShowRate, byStatus, byTherapist, daily);
    }

    private static AppointmentStatus mapAppointmentStatus(String s) {
        if (s == null) return AppointmentStatus.CONFIRMED;
        return switch (s) {
            case "Confirmed" -> AppointmentStatus.CONFIRMED;
            case "Arrived" -> AppointmentStatus.ARRIVED;
            case "InService" -> AppointmentStatus.IN_SERVICE;
            case "Completed" -> AppointmentStatus.COMPLETED;
            case "Cancelled" -> AppointmentStatus.CANCELLED;
            case "Rescheduled" -> AppointmentStatus.RESCHEDULED;
            case "NoShow" -> AppointmentStatus.NO_SHOW;
            default -> {
                try {
                    yield AppointmentStatus.valueOf(s.toUpperCase());
                } catch (Exception e) {
                    yield AppointmentStatus.CONFIRMED;
                }
            }
        };
    }

    public RevenueReportResponse revenue(ReportPeriod period, String branchCode) {
        Long branchId = resolveBranchId(branchCode);
        LocalDate from = period.from();
        LocalDate to = period.to();
        String normBranch = normalise(branchCode);

        String revSql = """
                select coalesce(sum(pt.amount), 0)
                from payment_transaction pt
                where pt.payment_status = true
                  and cast(pt.payment_date as date) >= ? and cast(pt.payment_date as date) <= ?
                """ + (branchId != null ? " and pt.branch_id = ?" : "");
        BigDecimal totalRevenue = branchId != null
                ? jdbc.queryForObject(revSql, BigDecimal.class, from, to, branchId)
                : jdbc.queryForObject(revSql, BigDecimal.class, from, to);
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        // Category breakdown
        Map<String, BigDecimal> catMap = paidCategoryTotals(period, branchId);

        List<RevenueReportResponse.CategoryRevenue> byCategory = new ArrayList<>();
        for (SaleItemType type : SaleItemType.values()) {
            String dbType = switch (type) {
                case COURSE -> "CoursePurchase";
                case SINGLE_VISIT -> "SingleVisit";
                case ASSESSMENT -> "Assessment";
            };
            BigDecimal amt = catMap.getOrDefault(dbType, BigDecimal.ZERO);
            BigDecimal share = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                    ? amt.divide(totalRevenue, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            byCategory.add(new RevenueReportResponse.CategoryRevenue(type, amt, share));
        }

        // By branch
        String bSql = """
                select b.branch_code, b.branch_name, coalesce(sum(pt.amount), 0) as amt
                from payment_transaction pt
                join branch b on b.branch_id = pt.branch_id
                where pt.payment_status = true
                  and cast(pt.payment_date as date) >= ? and cast(pt.payment_date as date) <= ?
                """ + (branchId != null ? " and pt.branch_id = ?\n" : "\n") + """
                group by b.branch_code, b.branch_name
                order by b.branch_code asc
                """;
        List<RevenueReportResponse.BranchRevenue> byBranch = jdbc.query(bSql, (rs, rowNum) -> new RevenueReportResponse.BranchRevenue(
                rs.getString("branch_code"), rs.getString("branch_name"), rs.getBigDecimal("amt")
        ), branchId != null ? new Object[]{from, to, branchId} : new Object[]{from, to});

        // Daily
        String dSql = """
                select cast(pt.payment_date as date) as pdate, coalesce(sum(pt.amount), 0) as amt
                from payment_transaction pt
                where pt.payment_status = true
                  and cast(pt.payment_date as date) >= ? and cast(pt.payment_date as date) <= ?
                """ + (branchId != null ? " and pt.branch_id = ?\n" : "\n") + """
                group by cast(pt.payment_date as date)
                order by pdate asc
                """;
        Map<LocalDate, BigDecimal> dailyMap = new HashMap<>();
        if (branchId != null) {
            jdbc.query(dSql, (rs) -> { dailyMap.put(rs.getObject("pdate", LocalDate.class), rs.getBigDecimal("amt")); }, from, to, branchId);
        } else {
            jdbc.query(dSql, (rs) -> { dailyMap.put(rs.getObject("pdate", LocalDate.class), rs.getBigDecimal("amt")); }, from, to);
        }

        List<RevenueReportResponse.DailyRevenue> daily = new ArrayList<>();
        LocalDate curr = from;
        while (!curr.isAfter(to)) {
            daily.add(new RevenueReportResponse.DailyRevenue(curr, dailyMap.getOrDefault(curr, BigDecimal.ZERO)));
            curr = curr.plusDays(1);
        }

        return new RevenueReportResponse(from, to, normBranch, totalRevenue, byCategory, byBranch, daily);
    }

    public CourseBalanceReportResponse courseBalances(ReportPeriod period, String branchCode, Long patientId) {
        Long branchId = resolveBranchId(branchCode);
        LocalDate from = period.from();
        LocalDate to = period.to();
        String normBranch = normalise(branchCode);

        if (patientId != null) {
            Integer exists = jdbc.queryForObject("select count(*) from patient where patient_id = ?", Integer.class, patientId);
            if (exists == null || exists == 0) {
                throw new DatabaseV2NotFoundException("ไม่พบผู้ป่วย id " + patientId);
            }
        }

        StringBuilder sql = new StringBuilder("""
                select c.course_id, c.course_no, c.course_sequence, c.course_name, c.course_price,
                       c.total_visits, coalesce(c.bonus_visits, 0) as bonus_visits, c.expiry_date,
                       cast(c.course_status as varchar) as status,
                       s.service_code,
                       r.receipt_id, r.receipt_no, r.sale_date,
                       p.patient_id, p.hn,
                       concat(p.patient_fname, case when p.patient_lname is not null and p.patient_lname <> '-' then concat(' ', p.patient_lname) else '' end) as patient_name,
                       b.branch_code,
                       c.visits_used, c.remaining_visits,
                       c.course_create_at
                from course c
                join receipt r on r.receipt_id = c.receipt_id
                left join course_shared_member csm on csm.course_id = c.course_id and csm.member_role = 'Owner'
                join patient p on p.patient_id = coalesce(csm.patient_id, r.patient_id)
                join branch b on b.branch_id = r.branch_id
                join service s on s.service_id = c.service_id
                where r.sale_date >= ? and r.sale_date <= ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(from);
        params.add(to);

        if (branchId != null) {
            sql.append(" and r.branch_id = ?");
            params.add(branchId);
        }
        if (patientId != null) {
            sql.append(" and p.patient_id = ?");
            params.add(patientId);
        }

        sql.append(" order by c.course_id desc");

        List<CourseBalanceReportResponse.Row> rows = jdbc.query(sql.toString(), (rs, rowNum) -> {
            LocalDate expDate = rs.getObject("expiry_date", LocalDate.class);
            boolean expired = expDate != null && expDate.isBefore(DatabaseV2BusinessDates.today());
            String st = rs.getString("status");
            CoursePurchaseStatus statusEnum;
            try {
                statusEnum = CoursePurchaseStatus.valueOf(st.toUpperCase());
            } catch (Exception e) {
                statusEnum = CoursePurchaseStatus.ACTIVE;
            }

            int purchased = rs.getInt("total_visits") + rs.getInt("bonus_visits");
            return new CourseBalanceReportResponse.Row(
                    rs.getLong("course_id"),
                    rs.getLong("patient_id"),
                    rs.getString("hn"),
                    rs.getString("patient_name"),
                    rs.getString("branch_code"),
                    rs.getString("service_code"),
                    rs.getString("course_name"),
                    rs.getBigDecimal("course_price"),
                    purchased,
                    rs.getInt("visits_used"),
                    rs.getInt("remaining_visits"),
                    rs.getTimestamp("course_create_at").toInstant(),
                    expDate != null ? expDate.atStartOfDay(BANGKOK_ZONE).toInstant() : null,
                    expired,
                    statusEnum
            );
        }, params.toArray());

        int purchases = rows.size();
        long totalPurchased = rows.stream().mapToLong(CourseBalanceReportResponse.Row::purchased).sum();
        long totalUsed = rows.stream().mapToLong(CourseBalanceReportResponse.Row::used).sum();
        long totalRemaining = rows.stream().mapToLong(CourseBalanceReportResponse.Row::remaining).sum();
        BigDecimal totalValue = rows.stream().map(CourseBalanceReportResponse.Row::price).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CourseBalanceReportResponse(
                from, to, normBranch, purchases, totalPurchased, totalUsed, totalRemaining, totalValue, rows
        );
    }

    public StaffSalesReportResponse staffSales(ReportPeriod period, String branchCode) {
        Long branchId = resolveBranchId(branchCode);
        LocalDate from = period.from();
        LocalDate to = period.to();
        String normBranch = normalise(branchCode);

        String base = """
                from sales_transaction st
                join branch b on b.branch_id = st.branch_id
                left join staff s on s.staff_id = st.salesperson_staff_id
                where st.transaction_status = true
                  and cast(st.transaction_date as date) >= ? and cast(st.transaction_date as date) <= ?
                """ + (branchId != null ? " and st.branch_id = ?\n" : "\n");
        Object[] params = branchId != null ? new Object[]{from, to, branchId} : new Object[]{from, to};

        BigDecimal branchTotal = jdbc.queryForObject("select coalesce(sum(st.amount), 0) " + base, BigDecimal.class, params);
        if (branchTotal == null) branchTotal = BigDecimal.ZERO;

        Long receipts = jdbc.queryForObject("select count(distinct st.course_id) " + base, Long.class, params);
        if (receipts == null) receipts = 0L;

        String staffSql = "select st.salesperson_staff_id, s.staff_code, concat(s.staff_fname, ' ', s.staff_lname) as full_name, count(*) as cnt, coalesce(sum(st.amount), 0) as amt "
                + base + " group by st.salesperson_staff_id, s.staff_code, s.staff_fname, s.staff_lname order by amt desc";

        List<StaffSalesReportResponse.Row> rows = new ArrayList<>();
        StaffSalesReportResponse.Row unassigned = null;

        List<Map<String, Object>> list = jdbc.queryForList(staffSql, params);
        for (Map<String, Object> map : list) {
            Long staffId = (Long) map.get("salesperson_staff_id");
            String code = (String) map.get("staff_code");
            String name = (String) map.get("full_name");
            long cnt = ((Number) map.get("cnt")).longValue();
            BigDecimal amt = (BigDecimal) map.get("amt");

            if (staffId == null) {
                unassigned = new StaffSalesReportResponse.Row(null, null, "ไม่ระบุผู้ขาย", cnt, amt);
            } else {
                rows.add(new StaffSalesReportResponse.Row(staffId, code, name, cnt, amt));
            }
        }

        if (unassigned == null) {
            unassigned = new StaffSalesReportResponse.Row(null, null, "ไม่ระบุผู้ขาย", 0, BigDecimal.ZERO);
        }

        return new StaffSalesReportResponse(from, to, normBranch, branchTotal, receipts, rows, unassigned);
    }
}
