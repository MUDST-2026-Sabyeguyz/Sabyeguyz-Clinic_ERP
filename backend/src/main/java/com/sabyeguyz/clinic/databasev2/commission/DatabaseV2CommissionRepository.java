package com.sabyeguyz.clinic.databasev2.commission;

import com.sabyeguyz.clinic.databasev2.common.DatabaseV2ConflictException;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-v2")
public class DatabaseV2CommissionRepository {

    public record SchemeRecord(
            Long id,
            String name,
            String version,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active
    ) {}

    public record TierRecord(
            Long id,
            BigDecimal minimum,
            BigDecimal maximum,
            BigDecimal rate,
            int order
    ) {}

    public record TierInput(
            BigDecimal minimum,
            BigDecimal maximum,
            BigDecimal rate,
            int order
    ) {}

    public record ReportRow(
            Long staffId,
            String staffName,
            String branchCode,
            String branchName,
            int saleCount,
            BigDecimal base,
            BigDecimal rate,
            BigDecimal commission
    ) {}

    public record TreatmentReportRow(
            Long id,
            LocalDate visitDate,
            Long coursePurchaseId,
            String courseCode,
            Long patientId,
            String patientHn,
            String patientName,
            String branchCode,
            String branchName,
            Long treatingStaffId,
            String treatingStaffName,
            Long ownerStaffId,
            String ownerStaffName,
            BigDecimal gross,
            BigDecimal treatmentFee,
            BigDecimal ownerNet
    ) {}

    public record ClosingRecord(
            Long id,
            LocalDate month,
            Long employeeId,
            String employeeName,
            BigDecimal sales,
            String version,
            BigDecimal rate,
            String status
    ) {}

    public record FeeRuleRecord(
            Long id,
            Long employeeId,
            String feeType,
            BigDecimal feeValue,
            String percentageBase,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active
    ) {}

    private final JdbcTemplate jdbc;

    public DatabaseV2CommissionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Long getOrCreateCommissionPermission(Long actorId) {
        List<Long> ids = jdbc.queryForList("select permission_id from permission where permission_name = 'COMMISSION_SCHEME_MANAGE'", Long.class);
        if (!ids.isEmpty()) return ids.get(0);
        return jdbc.queryForObject(
                "insert into permission (permission_name, module_name, permission_create_at, permission_create_by) values ('COMMISSION_SCHEME_MANAGE', 'Commission', current_timestamp, ?) returning permission_id",
                Long.class, actorId
        );
    }

    public List<SchemeRecord> listSchemes() {
        String sql = """
                select commission_scheme_id, scheme_name, scheme_version, effective_from, effective_to
                from commission_scheme
                order by effective_from desc, commission_scheme_id desc
                """;
        LocalDate today = LocalDate.now();
        return jdbc.query(sql, (rs, rowNum) -> {
            LocalDate to = rs.getObject("effective_to", LocalDate.class);
            boolean active = to == null || !to.isBefore(today);
            return new SchemeRecord(
                    rs.getLong("commission_scheme_id"),
                    rs.getString("scheme_name"),
                    String.valueOf(rs.getInt("scheme_version")),
                    rs.getObject("effective_from", LocalDate.class),
                    to,
                    active
            );
        });
    }

    public Optional<SchemeRecord> findSchemeById(Long id) {
        String sql = """
                select commission_scheme_id, scheme_name, scheme_version, effective_from, effective_to
                from commission_scheme
                where commission_scheme_id = ?
                """;
        LocalDate today = LocalDate.now();
        return jdbc.query(sql, (rs, rowNum) -> {
            LocalDate to = rs.getObject("effective_to", LocalDate.class);
            boolean active = to == null || !to.isBefore(today);
            return new SchemeRecord(
                    rs.getLong("commission_scheme_id"),
                    rs.getString("scheme_name"),
                    String.valueOf(rs.getInt("scheme_version")),
                    rs.getObject("effective_from", LocalDate.class),
                    to,
                    active
            );
        }, id).stream().findFirst();
    }

    public List<TierRecord> listTiers(Long schemeId) {
        String sql = """
                select tier_id, minimum_monthly_sales, maximum_monthly_sales, commission_rate, tier_order
                from commission_tier
                where commission_scheme_id = ? and tier_status = true
                order by tier_order asc
                """;
        return jdbc.query(sql, (rs, rowNum) -> new TierRecord(
                rs.getLong("tier_id"),
                rs.getBigDecimal("minimum_monthly_sales"),
                rs.getBigDecimal("maximum_monthly_sales"),
                rs.getBigDecimal("commission_rate"),
                rs.getInt("tier_order")
        ), schemeId);
    }

    public SchemeRecord createScheme(String name, String version, LocalDate from, LocalDate to, List<TierInput> tiers, Long actorId) {
        validateTiers(tiers);
        Long permId = getOrCreateCommissionPermission(actorId);
        int ver = 1;
        try {
            ver = Integer.parseInt(version.trim());
        } catch (NumberFormatException ignored) {}

        Long schemeId = jdbc.queryForObject(
                """
                insert into commission_scheme (
                    scheme_name, scheme_version, permission_id, effective_from, effective_to,
                    scheme_create_at, scheme_create_by
                ) values (?, ?, ?, ?, ?, current_timestamp, ?)
                returning commission_scheme_id
                """,
                Long.class,
                name.trim(), ver, permId, from, to, actorId
        );

        insertTiers(schemeId, permId, from, to, tiers, actorId);
        return findSchemeById(schemeId).orElseThrow();
    }

    public SchemeRecord updateScheme(Long id, String name, String version, LocalDate from, LocalDate to, List<TierInput> tiers, Boolean active, Long actorId) {
        validateTiers(tiers);
        Long permId = getOrCreateCommissionPermission(actorId);
        int ver = 1;
        try {
            ver = Integer.parseInt(version.trim());
        } catch (NumberFormatException ignored) {}

        LocalDate effectiveTo = to;
        if (Boolean.FALSE.equals(active)) {
            if (effectiveTo == null || effectiveTo.isAfter(LocalDate.now())) {
                effectiveTo = LocalDate.now().minusDays(1);
            }
        } else if (Boolean.TRUE.equals(active) && effectiveTo != null && effectiveTo.isBefore(LocalDate.now())) {
            effectiveTo = null;
        }

        int rows = jdbc.update(
                """
                update commission_scheme set
                    scheme_name = ?,
                    scheme_version = ?,
                    effective_from = ?,
                    effective_to = ?,
                    scheme_update_at = current_timestamp,
                    scheme_update_by = ?
                where commission_scheme_id = ?
                """,
                name.trim(), ver, from, effectiveTo, actorId, id
        );
        if (rows == 0) throw new DatabaseV2NotFoundException("commission scheme not found");

        jdbc.update("update commission_tier set tier_status = false, tier_update_at = current_timestamp, tier_update_by = ? where commission_scheme_id = ?", actorId, id);
        insertTiers(id, permId, from, effectiveTo, tiers, actorId);

        return findSchemeById(id).orElseThrow();
    }

    public SchemeRecord activateScheme(Long id, Long actorId) {
        int rows = jdbc.update(
                "update commission_scheme set effective_to = null, scheme_update_at = current_timestamp, scheme_update_by = ? where commission_scheme_id = ?",
                actorId, id
        );
        if (rows == 0) throw new DatabaseV2NotFoundException("commission scheme not found");
        return findSchemeById(id).orElseThrow();
    }

    public void deactivateScheme(Long id, Long actorId) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        int rows = jdbc.update(
                "update commission_scheme set effective_to = ?, scheme_update_at = current_timestamp, scheme_update_by = ? where commission_scheme_id = ?",
                yesterday, actorId, id
        );
        if (rows == 0) throw new DatabaseV2NotFoundException("commission scheme not found");
    }

    public SchemeRecord setActive(Long id, boolean active, Long actorId) {
        if (active) return activateScheme(id, actorId);
        deactivateScheme(id, actorId);
        return findSchemeById(id).orElseThrow();
    }

    private void insertTiers(Long schemeId, Long permId, LocalDate from, LocalDate to, List<TierInput> tiers, Long actorId) {
        for (TierInput t : tiers.stream().sorted(Comparator.comparingInt(TierInput::order)).toList()) {
            jdbc.update(
                    """
                    insert into commission_tier (
                        commission_scheme_id, permission_id, tier_order,
                        minimum_monthly_sales, maximum_monthly_sales, commission_rate, tier_status,
                        effective_from, effective_to, tier_create_at, tier_create_by
                    ) values (?, ?, ?, ?, ?, ?, true, ?, ?, current_timestamp, ?)
                    """,
                    schemeId, permId, t.order(),
                    t.minimum(), t.maximum(), t.rate(),
                    from, to, actorId
            );
        }
    }

    public void validateTiers(List<TierInput> inputs) {
        if (inputs == null || inputs.isEmpty()) throw new IllegalArgumentException("at least one commission tier is required");
        List<TierInput> ordered = inputs.stream().sorted(Comparator.comparingInt(TierInput::order)).toList();
        BigDecimal expectedMinimum = BigDecimal.ZERO;
        for (TierInput tier : ordered) {
            if (tier.order() < 1 || tier.minimum() == null || tier.minimum().compareTo(BigDecimal.ZERO) < 0
                    || tier.rate() == null || tier.rate().compareTo(BigDecimal.ZERO) < 0 || tier.rate().compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("invalid commission tier");
            }
            if (tier.maximum() != null && tier.maximum().compareTo(tier.minimum()) < 0) {
                throw new IllegalArgumentException("tier minimum exceeds maximum");
            }
            if (tier.minimum().compareTo(expectedMinimum) != 0) {
                throw new IllegalArgumentException("commission tier ranges must be contiguous and non-overlapping");
            }
            if (tier.maximum() == null && tier != ordered.get(ordered.size() - 1)) {
                throw new IllegalArgumentException("open-ended tier must be last");
            }
            if (tier.maximum() != null) expectedMinimum = tier.maximum().add(new BigDecimal("0.01"));
        }
    }

    public BigDecimal resolveRate(LocalDate date, BigDecimal monthlySales) {
        String sql = """
                select ct.commission_rate
                from commission_tier ct
                join commission_scheme cs on cs.commission_scheme_id = ct.commission_scheme_id
                where ct.tier_status = true
                  and cs.effective_from <= ?
                  and (cs.effective_to is null or cs.effective_to >= ?)
                  and ct.minimum_monthly_sales <= ?
                  and (ct.maximum_monthly_sales is null or ct.maximum_monthly_sales >= ?)
                order by cs.effective_from desc, ct.tier_order asc
                limit 1
                """;
        List<BigDecimal> rates = jdbc.queryForList(sql, BigDecimal.class, date, date, monthlySales, monthlySales);
        if (!rates.isEmpty()) return rates.get(0);
        return new BigDecimal("0.05"); // Fallback standard rate
    }

    public List<ReportRow> report(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("invalid report date range");
        }
        String sql = """
                select st.salesperson_staff_id,
                       concat(s.staff_fname, case when s.staff_lname is not null and s.staff_lname <> '-' then concat(' ', s.staff_lname) else '' end) as staff_name,
                       b.branch_code,
                       b.branch_name,
                       count(*) as sale_count,
                       coalesce(sum(st.amount), 0) as total_sales
                from sales_transaction st
                left join staff s on s.staff_id = st.salesperson_staff_id
                left join branch b on b.branch_id = st.branch_id
                where st.transaction_status = true
                  and cast(st.transaction_date as date) >= ?
                  and cast(st.transaction_date as date) <= ?
                group by st.salesperson_staff_id, s.staff_fname, s.staff_lname, b.branch_code, b.branch_name
                order by total_sales desc
                """;
        return jdbc.query(sql, (rs, rowNum) -> {
            Long staffId = (Long) rs.getObject("salesperson_staff_id");
            String staffName = rs.getString("staff_name");
            if (staffName == null || staffName.isBlank()) staffName = "ไม่ระบุ";
            String branchCode = rs.getString("branch_code");
            String branchName = rs.getString("branch_name");
            int count = rs.getInt("sale_count");
            BigDecimal base = rs.getBigDecimal("total_sales");
            BigDecimal rate = resolveRate(to, base);
            BigDecimal commission = base.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            return new ReportRow(staffId, staffName, branchCode, branchName, count, base, rate, commission);
        }, from, to);
    }

    public List<TreatmentReportRow> treatmentReport(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("invalid report date range");
        }
        String sql = """
                select cvu.visit_usage_id as usage_id,
                       cvu.visit_date,
                       c.course_id,
                       c.course_no,
                       p.patient_id,
                       p.hn,
                       concat(p.patient_fname, case when p.patient_lname is not null and p.patient_lname <> '-' then concat(' ', p.patient_lname) else '' end) as patient_name,
                       b.branch_code,
                       b.branch_name,
                       cvu.treating_staff_id,
                       concat(ts.staff_fname, ' ', ts.staff_lname) as treating_name,
                       cvu.case_owner_staff_id,
                       concat(os.staff_fname, ' ', os.staff_lname) as owner_name,
                       cvu.gross_commission_allocation,
                       cvu.substitute_treatment_fee_amount,
                       cvu.owner_net_commission_released
                from course_visit_usage cvu
                join course c on c.course_id = cvu.course_id
                join receipt r on r.receipt_id = c.receipt_id
                join patient p on p.patient_id = r.patient_id
                join branch b on b.branch_id = r.branch_id
                left join staff ts on ts.staff_id = cvu.treating_staff_id
                left join staff os on os.staff_id = cvu.case_owner_staff_id
                where cvu.visit_date >= ? and cvu.visit_date <= ?
                order by cvu.visit_date desc, cvu.visit_usage_id desc
                """;
        return jdbc.query(sql, (rs, rowNum) -> new TreatmentReportRow(
                rs.getLong("usage_id"),
                rs.getObject("visit_date", LocalDate.class),
                rs.getLong("course_id"),
                rs.getString("course_no"),
                rs.getLong("patient_id"),
                rs.getString("hn"),
                rs.getString("patient_name"),
                rs.getString("branch_code"),
                rs.getString("branch_name"),
                (Long) rs.getObject("treating_staff_id"),
                rs.getString("treating_name") != null ? rs.getString("treating_name") : "ไม่ระบุ",
                (Long) rs.getObject("case_owner_staff_id"),
                rs.getString("owner_name") != null ? rs.getString("owner_name") : "ไม่ระบุ",
                rs.getBigDecimal("gross_commission_allocation"),
                rs.getBigDecimal("substitute_treatment_fee_amount"),
                rs.getBigDecimal("owner_net_commission_released")
        ), from, to);
    }

    public List<ClosingRecord> findClosings(LocalDate month) {
        String monthStr = String.format("%04d-%02d", month.getYear(), month.getMonthValue());
        String sql = """
                select mc.closing_id,
                       mc.closing_month,
                       mc.staff_id,
                       concat(s.staff_fname, case when s.staff_lname is not null and s.staff_lname <> '-' then concat(' ', s.staff_lname) else '' end) as employee_name,
                       mc.monthly_course_sales,
                       mc.commission_scheme_version,
                       coalesce(mc.locked_commission_rate, mc.calculated_commission_rate) as rate,
                       case when mc.closing_status = true then 'CLOSED' else 'OPEN' end as status
                from monthly_commission_closing mc
                join staff s on s.staff_id = mc.staff_id
                where mc.closing_month = ?
                order by mc.closing_id asc
                """;
        return jdbc.query(sql, (rs, rowNum) -> new ClosingRecord(
                rs.getLong("closing_id"),
                month.withDayOfMonth(1),
                rs.getLong("staff_id"),
                rs.getString("employee_name"),
                rs.getBigDecimal("monthly_course_sales"),
                String.valueOf(rs.getInt("commission_scheme_version")),
                rs.getBigDecimal("rate"),
                rs.getString("status")
        ), monthStr);
    }

    public List<ClosingRecord> closeMonth(LocalDate month, Long actorId) {
        String monthStr = String.format("%04d-%02d", month.getYear(), month.getMonthValue());
        LocalDate monthStart = month.withDayOfMonth(1);
        Long permId = getOrCreateCommissionPermission(actorId);

        // Find active scheme
        List<Long> schemeIds = jdbc.queryForList(
                "select commission_scheme_id from commission_scheme where effective_from <= ? and (effective_to is null or effective_to >= ?) order by effective_from desc limit 1",
                Long.class, monthStart, monthStart
        );
        Long schemeId;
        int schemeVersion = 1;
        if (!schemeIds.isEmpty()) {
            schemeId = schemeIds.get(0);
            Integer ver = jdbc.queryForObject("select scheme_version from commission_scheme where commission_scheme_id = ?", Integer.class, schemeId);
            if (ver != null) schemeVersion = ver;
        } else {
            schemeId = getOrCreateDefaultCommissionScheme(actorId);
        }

        // Sellers with course sales in this month
        List<Long> sellers = jdbc.queryForList(
                "select distinct seller_staff_id from course where sale_month = ? and seller_staff_id is not null and course_status <> 'Cancelled'",
                Long.class, monthStr
        );

        List<ClosingRecord> results = new ArrayList<>();
        for (Long sellerId : sellers) {
            BigDecimal totalSales = jdbc.queryForObject(
                    "select coalesce(sum(course_price), 0) from course where sale_month = ? and seller_staff_id = ? and course_status <> 'Cancelled'",
                    BigDecimal.class, monthStr, sellerId
            );
            if (totalSales == null) totalSales = BigDecimal.ZERO;

            BigDecimal rate = resolveRate(monthStart, totalSales);

            // Check if existing closing record
            List<Long> existing = jdbc.queryForList(
                    "select closing_id from monthly_commission_closing where closing_month = ? and staff_id = ?",
                    Long.class, monthStr, sellerId
            );
            Long closingId;
            if (existing.isEmpty()) {
                closingId = jdbc.queryForObject(
                        """
                        insert into monthly_commission_closing (
                            closing_month, permission_id, staff_id, monthly_course_sales,
                            commission_scheme_id, commission_scheme_version, calculated_commission_rate,
                            locked_commission_rate, closing_status, closing_at, closed_by
                        ) values (
                            ?, ?, ?, ?,
                            ?, ?, ?,
                            ?, true, current_timestamp, ?
                        ) returning closing_id
                        """,
                        Long.class,
                        monthStr, permId, sellerId, totalSales,
                        schemeId, schemeVersion, rate,
                        rate, actorId
                );
            } else {
                closingId = existing.get(0);
                jdbc.update(
                        """
                        update monthly_commission_closing set
                            monthly_course_sales = ?,
                            commission_scheme_id = ?,
                            commission_scheme_version = ?,
                            calculated_commission_rate = ?,
                            locked_commission_rate = ?,
                            closing_status = true,
                            closing_at = current_timestamp,
                            closed_by = ?
                        where closing_id = ?
                        """,
                        totalSales, schemeId, schemeVersion, rate, rate, actorId, closingId
                );
            }

            // Lock course commission rates
            jdbc.update(
                    """
                    update course set
                        commission_status = 'Locked',
                        locked_commission_rate = ?,
                        total_course_commission_pool = course_price * ?,
                        commission_allocation_per_visit = (course_price * ?) / greatest(1, total_visits),
                        commission_generated_date = current_date,
                        course_update_at = current_timestamp,
                        course_update_by = ?
                    where sale_month = ? and seller_staff_id = ? and course_status <> 'Cancelled'
                    """,
                    rate, rate, rate, actorId, monthStr, sellerId
            );

            // Fetch record to return
            String staffName = jdbc.queryForObject(
                    "select concat(staff_fname, ' ', staff_lname) from staff where staff_id = ?",
                    String.class, sellerId
            );
            results.add(new ClosingRecord(
                    closingId, monthStart, sellerId, staffName, totalSales, String.valueOf(schemeVersion), rate, "CLOSED"
            ));
        }

        return results;
    }

    public ClosingRecord closeSingle(Long id, Long actorId) {
        List<MonthlyClosingEntity> list = jdbc.query(
                "select closing_id, closing_month, staff_id, monthly_course_sales, commission_scheme_version, calculated_commission_rate, locked_commission_rate, closing_status from monthly_commission_closing where closing_id = ?",
                (rs, rowNum) -> new MonthlyClosingEntity(
                        rs.getLong("closing_id"),
                        rs.getString("closing_month"),
                        rs.getLong("staff_id"),
                        rs.getBigDecimal("monthly_course_sales"),
                        rs.getInt("commission_scheme_version"),
                        rs.getBigDecimal("calculated_commission_rate"),
                        rs.getBigDecimal("locked_commission_rate"),
                        rs.getBoolean("closing_status")
                ), id
        );
        if (list.isEmpty()) throw new DatabaseV2NotFoundException("ไม่พบรอบคอมมิชชัน id " + id);
        MonthlyClosingEntity c = list.get(0);
        if (c.closed()) throw new DatabaseV2ConflictException("รอบนี้ปิดแล้ว");

        BigDecimal rate = c.lockedRate() != null ? c.lockedRate() : c.calculatedRate();

        jdbc.update(
                """
                update monthly_commission_closing set
                    closing_status = true,
                    locked_commission_rate = ?,
                    closing_at = current_timestamp,
                    closed_by = ?
                where closing_id = ?
                """,
                rate, actorId, id
        );

        // Lock courses
        jdbc.update(
                """
                update course set
                    commission_status = 'Locked',
                    locked_commission_rate = ?,
                    total_course_commission_pool = course_price * ?,
                    commission_allocation_per_visit = (course_price * ?) / greatest(1, total_visits),
                    commission_generated_date = current_date,
                    course_update_at = current_timestamp,
                    course_update_by = ?
                where sale_month = ? and seller_staff_id = ? and course_status <> 'Cancelled'
                """,
                rate, rate, rate, actorId, c.monthStr(), c.staffId()
        );

        String staffName = jdbc.queryForObject(
                "select concat(staff_fname, ' ', staff_lname) from staff where staff_id = ?",
                String.class, c.staffId()
        );
        LocalDate monthDate = LocalDate.parse(c.monthStr() + "-01");
        return new ClosingRecord(
                id, monthDate, c.staffId(), staffName, c.sales(), String.valueOf(c.schemeVersion()), rate, "CLOSED"
        );
    }

    private record MonthlyClosingEntity(
            Long id, String monthStr, Long staffId, BigDecimal sales, int schemeVersion,
            BigDecimal calculatedRate, BigDecimal lockedRate, boolean closed
    ) {}

    public List<FeeRuleRecord> listFeeRules() {
        String sql = """
                select treatment_fee_rule_id, staff_id, cast(fee_type as varchar) as fee_type,
                       fee_value, cast(calculation_base as varchar) as calculation_base,
                       effective_from, effective_to, treatment_fee_rule_status
                from treatment_fee_rule
                order by treatment_fee_rule_id desc
                """;
        return jdbc.query(sql, (rs, rowNum) -> new FeeRuleRecord(
                rs.getLong("treatment_fee_rule_id"),
                (Long) rs.getObject("staff_id"),
                rs.getString("fee_type"),
                rs.getBigDecimal("fee_value"),
                rs.getString("calculation_base"),
                rs.getObject("effective_from", LocalDate.class),
                rs.getObject("effective_to", LocalDate.class),
                rs.getBoolean("treatment_fee_rule_status")
        ));
    }

    public FeeRuleRecord createFeeRule(Long employeeId, String feeType, BigDecimal feeValue, String percentageBase, LocalDate from, LocalDate to, Long actorId) {
        if (to != null && !to.isAfter(from)) {
            throw new IllegalArgumentException("effective_to must be after effective_from");
        }
        Long permId = getOrCreateCommissionPermission(actorId);

        String mappedFeeType = (feeType != null && feeType.toUpperCase().startsWith("PERCENT")) ? "Percentage" : "Fixed";
        String mappedBase = "Percentage".equals(mappedFeeType) ? "CourseValuePerVisit" : null;

        Long ruleId = jdbc.queryForObject(
                """
                insert into treatment_fee_rule (
                    permission_id, staff_id, fee_type, fee_value, calculation_base,
                    effective_from, effective_to, treatment_fee_rule_status,
                    treatment_fee_rule_create_at, treatment_fee_rule_create_by
                ) values (
                    ?, ?, cast(? as fee_type_enum), ?, cast(? as calculation_base_enum),
                    ?, ?, true,
                    current_timestamp, ?
                ) returning treatment_fee_rule_id
                """,
                Long.class,
                permId, employeeId, mappedFeeType, feeValue, mappedBase,
                from, to, actorId
        );

        return new FeeRuleRecord(ruleId, employeeId, mappedFeeType, feeValue, mappedBase, from, to, true);
    }

    public void deactivateFeeRule(Long id, Long actorId) {
        jdbc.update(
                """
                update treatment_fee_rule set
                    treatment_fee_rule_status = false,
                    treatment_fee_rule_update_at = current_timestamp,
                    treatment_fee_rule_update_by = ?
                where treatment_fee_rule_id = ?
                """,
                actorId, id
        );
    }

    private Long getOrCreateDefaultCommissionScheme(Long actorId) {
        List<Long> ids = jdbc.queryForList("select commission_scheme_id from commission_scheme limit 1", Long.class);
        if (!ids.isEmpty()) return ids.get(0);
        Long permId = getOrCreateCommissionPermission(actorId);
        Long schemeId = jdbc.queryForObject(
                """
                insert into commission_scheme (
                    scheme_name, scheme_version, permission_id, effective_from,
                    scheme_create_at, scheme_create_by
                ) values ('Default Scheme', 1, ?, '2026-01-01', current_timestamp, ?)
                returning commission_scheme_id
                """,
                Long.class, permId, actorId
        );
        jdbc.update(
                """
                insert into commission_tier (
                    commission_scheme_id, permission_id, tier_order,
                    minimum_monthly_sales, maximum_monthly_sales, commission_rate, tier_status,
                    effective_from, tier_create_at, tier_create_by
                ) values (?, ?, 1, 0, null, 0.05, true, '2026-01-01', current_timestamp, ?)
                """,
                schemeId, permId, actorId
        );
        return schemeId;
    }
}
