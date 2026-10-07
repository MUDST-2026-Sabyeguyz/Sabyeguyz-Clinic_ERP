package com.sabyeguyz.clinic.databasev2.sale;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-v2")
public class DatabaseV2SaleRepository {

    public record GeneratedReceipt(Long id, String receiptNo) {}

    public record SaleItemRecord(
            Long id,
            String type,
            String description,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal,
            BigDecimal grossAmount,
            BigDecimal lineDiscountAmount,
            BigDecimal commissionBase,
            Long coursePurchaseId
    ) {
        public SaleItemRecord(Long id, String type, String description, BigDecimal unitPrice, int quantity,
                              BigDecimal lineTotal, Long coursePurchaseId) {
            this(id, type, description, unitPrice, quantity, lineTotal, lineTotal, BigDecimal.ZERO, lineTotal, coursePurchaseId);
        }
    }

    public record SalePaymentRecord(
            Long id,
            String type,
            String paymentMethodCode,
            BigDecimal amount,
            String reason,
            Long reversesPaymentId,
            LocalDateTime createdAt
    ) {}

    public record SaleRecord(
            Long id,
            String receiptNo,
            Long patientId,
            String patientHn,
            String patientFullName,
            String branchCode,
            Long salespersonId,
            String salespersonCode,
            String salespersonFullName,
            BigDecimal total,
            BigDecimal grossTotal,
            BigDecimal lineDiscountTotal,
            BigDecimal billDiscountAmount,
            BigDecimal commissionBase,
            BigDecimal paid,
            String status,
            String note,
            LocalDateTime soldAt,
            LocalDateTime voidedAt,
            String voidReason,
            List<SaleItemRecord> items,
            List<SalePaymentRecord> payments
    ) {
        public SaleRecord(Long id, String receiptNo, Long patientId, String patientHn, String patientFullName,
                          String branchCode, Long salespersonId, String salespersonCode, String salespersonFullName,
                          BigDecimal total, BigDecimal paid, String status, String note, LocalDateTime soldAt,
                          LocalDateTime voidedAt, String voidReason, List<SaleItemRecord> items,
                          List<SalePaymentRecord> payments) {
            this(id, receiptNo, patientId, patientHn, patientFullName, branchCode, salespersonId, salespersonCode,
                    salespersonFullName, total, total, BigDecimal.ZERO, BigDecimal.ZERO, total, paid, status, note,
                    soldAt, voidedAt, voidReason, items, payments);
        }
    }

    public record SaleSummaryRecord(
            Long id,
            String receiptNo,
            Long patientId,
            String patientHn,
            String patientFullName,
            String branchCode,
            Long salespersonId,
            String salespersonCode,
            String salespersonFullName,
            BigDecimal total,
            BigDecimal paid,
            String status,
            LocalDateTime soldAt,
            LocalDateTime voidedAt
    ) {}

    private final JdbcTemplate jdbc;

    public DatabaseV2SaleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public GeneratedReceipt allocateReceipt() {
        Long nextVal = jdbc.queryForObject("select nextval('receipt_receipt_id_seq')", Long.class);
        return new GeneratedReceipt(nextVal, String.format("RE-%07d", nextVal));
    }

    public void insertReceipt(Long receiptId, String receiptNo, Long patientId, Long branchId, LocalDate saleDate, Long actorId) {
        insertReceipt(receiptId, receiptNo, patientId, branchId, saleDate, actorId,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public void insertReceipt(Long receiptId, String receiptNo, Long patientId, Long branchId, LocalDate saleDate,
                              Long actorId, BigDecimal grossTotal, BigDecimal lineDiscountTotal,
                              BigDecimal billDiscountAmount, BigDecimal netDue) {
        jdbc.update(
                """
                insert into receipt (
                    receipt_id, receipt_no, patient_id, branch_id, sale_date,
                    receipt_create_at, receipt_create_by,
                    gross_total, line_discount_total, bill_discount_amount, net_due
                ) values (?, ?, ?, ?, ?, current_timestamp, ?, ?, ?, ?, ?)
                """,
                receiptId, receiptNo, patientId, branchId, saleDate, actorId,
                grossTotal, lineDiscountTotal, billDiscountAmount, netDue
        );
    }

    public Long createSalesTransaction(
            Long patientId, Long branchId, Long serviceId, String type,
            Long treatingStaffId, Long salespersonStaffId,
            int quantity, BigDecimal unitPrice, BigDecimal amount,
            LocalDateTime transactionDate, Long courseId, Long actorId
    ) {
        return createSalesTransactionForReceipt(null, patientId, branchId, serviceId, type,
                treatingStaffId, salespersonStaffId, quantity, unitPrice, amount, transactionDate, courseId, actorId);
    }

    public Long createSalesTransactionForReceipt(
            Long receiptId, Long patientId, Long branchId, Long serviceId, String type,
            Long treatingStaffId, Long salespersonStaffId,
            int quantity, BigDecimal unitPrice, BigDecimal amount,
            LocalDateTime transactionDate, Long courseId, Long actorId
    ) {
        return createSalesTransactionForReceipt(receiptId, patientId, branchId, serviceId, type,
                treatingStaffId, salespersonStaffId, quantity, unitPrice, amount, amount,
                BigDecimal.ZERO, transactionDate, courseId, actorId);
    }

    public Long createSalesTransactionForReceipt(
            Long receiptId, Long patientId, Long branchId, Long serviceId, String type,
            Long treatingStaffId, Long salespersonStaffId,
            int quantity, BigDecimal unitPrice, BigDecimal amount, BigDecimal grossAmount,
            BigDecimal lineDiscountAmount, LocalDateTime transactionDate, Long courseId, Long actorId
    ) {
        String dbType = switch (type) {
            case "COURSE" -> "CoursePurchase";
            case "ASSESSMENT" -> "Assessment";
            case "SINGLE_VISIT" -> "SingleVisit";
            default -> type;
        };

        return jdbc.queryForObject(
                """
                insert into sales_transaction (
                    receipt_id, patient_id, branch_id, service_id, transaction_type,
                    treating_staff_id, salesperson_staff_id,
                    quantity, unit_price, amount, transaction_date,
                    course_id, transaction_status, gross_amount, line_discount_amount,
                    transaction_create_at, transaction_create_by
                ) values (
                    ?, ?, ?, ?, cast(? as transaction_type_enum),
                    ?, ?,
                    ?, ?, ?, ?,
                    ?, true, ?, ?,
                    current_timestamp, ?
                ) returning transaction_id
                """,
                Long.class,
                receiptId, patientId, branchId, serviceId, dbType,
                treatingStaffId, salespersonStaffId,
                quantity, unitPrice, amount, transactionDate,
                courseId, grossAmount, lineDiscountAmount, actorId
        );
    }

    public void updateTransactionCourseId(Long transactionId, Long courseId) {
        jdbc.update("update sales_transaction set course_id = ? where transaction_id = ?", courseId, transactionId);
    }

    public Long createCourse(
            String courseNo, String courseName, BigDecimal price,
            int totalVisits, int bonusVisits, int validityDays,
            Long serviceId, Long receiptId, LocalDate saleDate,
            Long sellerStaffId, Long caseOwnerStaffId, Long actorId
    ) {
        LocalDate expiryDate = saleDate.plusDays(validityDays > 0 ? validityDays : 365);
        String saleMonth = String.format("%04d-%02d", saleDate.getYear(), saleDate.getMonthValue());
        int remaining = totalVisits + bonusVisits;
        Long schemeId = getOrCreateDefaultCommissionScheme(actorId);

        Long courseId = jdbc.queryForObject(
                """
                insert into course (
                    course_no, course_sequence, course_name, course_price,
                    total_visits, bonus_visits, expiry_date, course_status,
                    service_id, receipt_id, sale_month, seller_staff_id, case_owner_staff_id,
                    commission_status, commission_scheme_id, commission_scheme_version,
                    visits_used, remaining_visits, course_create_at, course_create_by
                ) values (
                    ?, 1, ?, ?,
                    ?, ?, ?, 'Active',
                    ?, ?, ?, ?, ?,
                    'Provisional', ?, 1,
                    0, ?, current_timestamp, ?
                ) returning course_id
                """,
                Long.class,
                courseNo, courseName, price,
                totalVisits, bonusVisits, expiryDate,
                serviceId, receiptId, saleMonth, sellerStaffId, caseOwnerStaffId,
                schemeId, remaining, actorId
        );

        jdbc.update(
                """
                insert into course_shared_member (course_id, patient_id, member_role, member_status)
                values (?, (select patient_id from receipt where receipt_id = ?), 'Owner', true)
                """,
                courseId, receiptId
        );

        jdbc.update(
                """
                insert into course_ledger (
                    course_id, patient_id, entry_type, qty_change, balance_after,
                    reference_type, reference_id, remark, ledger_create_at, ledger_create_by
                ) values (
                    ?, (select patient_id from receipt where receipt_id = ?), 'Purchase', ?, ?,
                    'RECEIPT', ?, 'Course purchase', current_timestamp, ?
                )
                """,
                courseId, receiptId, remaining, remaining, receiptId, actorId
        );

        return courseId;
    }

    public Long createPaymentTransaction(
            Long patientId, Long branchId, Long transactionId,
            BigDecimal amount, Long paymentMethodId, LocalDateTime paymentDate, Long actorId
    ) {
        return jdbc.queryForObject(
                """
                insert into payment_transaction (
                    patient_id, branch_id, transaction_id, amount,
                    payment_method_id, payment_date, payment_status,
                    payment_create_at, payment_create_by
                ) values (
                    ?, ?, ?, ?,
                    ?, ?, true,
                    current_timestamp, ?
                ) returning payment_id
                """,
                Long.class,
                patientId, branchId, transactionId, amount,
                paymentMethodId, paymentDate, actorId
        );
    }

    public Optional<SaleRecord> findById(Long receiptId) {
        String sql = """
                select r.receipt_id, r.receipt_no, r.sale_date, r.receipt_create_at,
                       r.gross_total, r.line_discount_total, r.bill_discount_amount, r.net_due,
                       p.patient_id, p.hn,
                       concat(p.patient_fname, case when p.patient_lname is not null and p.patient_lname <> '-' then concat(' ', p.patient_lname) else '' end) as patient_name,
                       b.branch_code,
                       (select st.salesperson_staff_id from sales_transaction st left join course c on c.course_id = st.course_id where (st.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED') or (st.receipt_id is null and c.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED') limit 1) as salesperson_id,
                       (select tc.reason from transaction_cancellation tc join sales_transaction st on st.transaction_id = tc.transaction_id left join course c on c.course_id = st.course_id where ((st.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED') or (st.receipt_id is null and c.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED')) limit 1) as void_reason,
                       (select tc.cancelled_at from transaction_cancellation tc join sales_transaction st on st.transaction_id = tc.transaction_id left join course c on c.course_id = st.course_id where ((st.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED') or (st.receipt_id is null and c.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED')) limit 1) as voided_at
                from receipt r
                join patient p on p.patient_id = r.patient_id
                join branch b on b.branch_id = r.branch_id
                where r.receipt_id = ?
                """;

        List<SaleRecord> results = jdbc.query(sql, (rs, rowNum) -> {
            Long sid = (Long) rs.getObject("salesperson_id");
            String scode = null;
            String sname = null;
            if (sid != null) {
                try {
                    scode = jdbc.queryForObject("select staff_code from staff where staff_id = ?", String.class, sid);
                    sname = jdbc.queryForObject("select concat(staff_fname, case when staff_lname is not null and staff_lname <> '-' then concat(' ', staff_lname) else '' end) from staff where staff_id = ?", String.class, sid);
                } catch (Exception ignored) {}
            }

            LocalDateTime voidedAt = rs.getObject("voided_at", LocalDateTime.class);
            String voidReason = rs.getString("void_reason");
            boolean isVoided = voidedAt != null;

            List<SaleItemRecord> items = findItemsForReceipt(receiptId);
            List<SalePaymentRecord> payments = findPaymentsForReceipt(receiptId, isVoided, voidReason, voidedAt);

            BigDecimal total = rs.getBigDecimal("net_due");
            BigDecimal grossTotal = rs.getBigDecimal("gross_total");
            BigDecimal lineDiscountTotal = rs.getBigDecimal("line_discount_total");
            BigDecimal billDiscountAmount = rs.getBigDecimal("bill_discount_amount");
            BigDecimal commissionBase = items.stream().map(SaleItemRecord::commissionBase).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal paid = isVoided ? BigDecimal.ZERO : payments.stream().filter(p -> "PAYMENT".equals(p.type()))
                    .map(SalePaymentRecord::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

            return new SaleRecord(
                    rs.getLong("receipt_id"),
                    rs.getString("receipt_no"),
                    rs.getLong("patient_id"),
                    rs.getString("hn"),
                    rs.getString("patient_name"),
                    rs.getString("branch_code"),
                    sid,
                    scode,
                    sname,
                    total,
                    grossTotal,
                    lineDiscountTotal,
                    billDiscountAmount,
                    commissionBase,
                    paid,
                    isVoided ? "VOIDED" : "PAID",
                    null,
                    rs.getObject("receipt_create_at", LocalDateTime.class),
                    voidedAt,
                    voidReason,
                    items,
                    payments
            );
        }, receiptId);

        return results.stream().findFirst();
    }

    public List<SaleItemRecord> findItemsForReceipt(Long receiptId) {
        String sql = """
                select st.transaction_id,
                       cast(st.transaction_type as varchar) as transaction_type,
                       s.service_name,
                       st.unit_price,
                       st.quantity,
                       (st.gross_amount - st.line_discount_amount) as line_total,
                       st.gross_amount,
                       st.line_discount_amount,
                       st.amount as commission_base,
                       st.course_id
                from sales_transaction st
                join service s on s.service_id = st.service_id
                left join course c on c.course_id = st.course_id
                where (st.receipt_id = ? and st.receipt_link_status = 'VERIFIED')
                   or (st.receipt_id is null and c.receipt_id = ? and st.receipt_link_status = 'VERIFIED')
                order by st.transaction_id asc
                """;

        return jdbc.query(sql, (rs, rowNum) -> {
            String dbType = rs.getString("transaction_type");
            String apiType = switch (dbType) {
                case "CoursePurchase" -> "COURSE";
                case "Assessment" -> "ASSESSMENT";
                case "SingleVisit" -> "SINGLE_VISIT";
                default -> dbType;
            };

            return new SaleItemRecord(
                    rs.getLong("transaction_id"),
                    apiType,
                    rs.getString("service_name"),
                    rs.getBigDecimal("unit_price"),
                    rs.getInt("quantity"),
                    rs.getBigDecimal("line_total"),
                    rs.getBigDecimal("gross_amount"),
                    rs.getBigDecimal("line_discount_amount"),
                    rs.getBigDecimal("commission_base"),
                    (Long) rs.getObject("course_id")
            );
        }, receiptId, receiptId);
    }

    public List<SalePaymentRecord> findPaymentsForReceipt(Long receiptId, boolean isVoided, String voidReason, LocalDateTime voidedAt) {
        String sql = """
                select pt.payment_id,
                       pm.payment_method_name,
                       pt.amount,
                       pt.payment_status,
                       pt.payment_create_at
                from payment_transaction pt
                join payment_method pm on pm.payment_method_id = pt.payment_method_id
                where pt.transaction_id in (
                    select st.transaction_id from sales_transaction st
                    left join course c on c.course_id = st.course_id
                    where (st.receipt_id = ? and st.receipt_link_status = 'VERIFIED')
                       or (st.receipt_id is null and c.receipt_id = ? and st.receipt_link_status = 'VERIFIED')
                )
                order by pt.payment_id asc
                """;

        List<SalePaymentRecord> list = new ArrayList<>();
        List<SalePaymentRecord> originals = jdbc.query(sql, (rs, rowNum) -> new SalePaymentRecord(
                rs.getLong("payment_id"),
                "PAYMENT",
                rs.getString("payment_method_name"),
                rs.getBigDecimal("amount"),
                null,
                null,
                rs.getObject("payment_create_at", LocalDateTime.class)
        ), receiptId, receiptId);

        list.addAll(originals);

        if (isVoided) {
            for (SalePaymentRecord orig : originals) {
                list.add(new SalePaymentRecord(
                        orig.id() + 1000000L,
                        "REFUND",
                        orig.paymentMethodCode(),
                        orig.amount().negate(),
                        voidReason != null ? voidReason : "Receipt voided",
                        orig.id(),
                        voidedAt != null ? voidedAt : LocalDateTime.now()
                ));
            }
        }

        return list;
    }

    public List<SaleSummaryRecord> search(Long patientId, String branchCode, int limit, int offset) {
        return searchInternal(patientId, branchCode, limit, offset, null);
    }

    public List<SaleSummaryRecord> searchAccessible(Long patientId, String branchCode, int limit, int offset, Long userId) {
        return searchInternal(patientId, branchCode, limit, offset, userId);
    }

    private List<SaleSummaryRecord> searchInternal(Long patientId, String branchCode, int limit, int offset, Long userId) {
        StringBuilder sql = new StringBuilder("""
                select r.receipt_id, r.receipt_no, r.sale_date, r.receipt_create_at,
                       p.patient_id, p.hn,
                       concat(p.patient_fname, case when p.patient_lname is not null and p.patient_lname <> '-' then concat(' ', p.patient_lname) else '' end) as patient_name,
                       b.branch_code,
                       (select st.salesperson_staff_id from sales_transaction st left join course c on c.course_id = st.course_id where (st.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED') or (st.receipt_id is null and c.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED') limit 1) as salesperson_id,
                       (select concat(stf.staff_fname, ' ', stf.staff_lname) from staff stf where stf.staff_id = (select st.salesperson_staff_id from sales_transaction st left join course c on c.course_id = st.course_id where (st.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED') or (st.receipt_id is null and c.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED') limit 1)) as salesperson_name,
                       (select stf.staff_code from staff stf where stf.staff_id = (select st.salesperson_staff_id from sales_transaction st left join course c on c.course_id = st.course_id where (st.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED') or (st.receipt_id is null and c.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED') limit 1)) as salesperson_code,
                       r.net_due as total_amount,
                       (select coalesce(sum(pt.amount), 0) from payment_transaction pt where pt.payment_status = true and pt.transaction_id in (select st.transaction_id from sales_transaction st left join course c on c.course_id = st.course_id where (st.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED') or (st.receipt_id is null and c.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED'))) as paid_amount,
                       (select count(*) from transaction_cancellation tc join sales_transaction st on st.transaction_id = tc.transaction_id left join course c on c.course_id = st.course_id where ((st.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED') or (st.receipt_id is null and c.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED'))) as void_count,
                       (select tc.cancelled_at from transaction_cancellation tc join sales_transaction st on st.transaction_id = tc.transaction_id left join course c on c.course_id = st.course_id where ((st.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED') or (st.receipt_id is null and c.receipt_id = r.receipt_id and st.receipt_link_status = 'VERIFIED')) limit 1) as voided_at
                from receipt r
                join patient p on p.patient_id = r.patient_id
                join branch b on b.branch_id = r.branch_id
                """);

        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (patientId != null) {
            conditions.add("r.patient_id = ?");
            params.add(patientId);
        }
        if (branchCode != null && !branchCode.isBlank()) {
            conditions.add("b.branch_code = ?");
            params.add(branchCode.trim().toUpperCase());
        }
        if (userId != null) {
            conditions.add("exists (select 1 from user_branch_access uba where uba.user_id = ? and uba.branch_id = r.branch_id)");
            params.add(userId);
        }

        if (!conditions.isEmpty()) {
            sql.append(" where ").append(String.join(" and ", conditions));
        }

        sql.append(" order by r.receipt_id desc limit ? offset ?");
        params.add(limit);
        params.add(offset);

        return jdbc.query(sql.toString(), (rs, rowNum) -> {
            boolean isVoided = rs.getInt("void_count") > 0;
            BigDecimal total = rs.getBigDecimal("total_amount");
            if (total == null) total = BigDecimal.ZERO;
            BigDecimal paid = isVoided ? BigDecimal.ZERO : rs.getBigDecimal("paid_amount");

            return new SaleSummaryRecord(
                    rs.getLong("receipt_id"),
                    rs.getString("receipt_no"),
                    rs.getLong("patient_id"),
                    rs.getString("hn"),
                    rs.getString("patient_name"),
                    rs.getString("branch_code"),
                    (Long) rs.getObject("salesperson_id"),
                    rs.getString("salesperson_code"),
                    rs.getString("salesperson_name"),
                    total,
                    paid,
                    isVoided ? "VOIDED" : "PAID",
                    rs.getObject("receipt_create_at", LocalDateTime.class),
                    rs.getObject("voided_at", LocalDateTime.class)
            );
        }, params.toArray());
    }

    public int countSearch(Long patientId, String branchCode) {
        return countSearchInternal(patientId, branchCode, null);
    }

    public int countSearchAccessible(Long patientId, String branchCode, Long userId) {
        return countSearchInternal(patientId, branchCode, userId);
    }

    private int countSearchInternal(Long patientId, String branchCode, Long userId) {
        StringBuilder sql = new StringBuilder("select count(*) from receipt r join branch b on b.branch_id = r.branch_id");
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (patientId != null) {
            conditions.add("r.patient_id = ?");
            params.add(patientId);
        }
        if (branchCode != null && !branchCode.isBlank()) {
            conditions.add("b.branch_code = ?");
            params.add(branchCode.trim().toUpperCase());
        }
        if (userId != null) {
            conditions.add("exists (select 1 from user_branch_access uba where uba.user_id = ? and uba.branch_id = r.branch_id)");
            params.add(userId);
        }

        if (!conditions.isEmpty()) {
            sql.append(" where ").append(String.join(" and ", conditions));
        }

        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    public void voidReceipt(Long receiptId, String reason, Long actorId) {
        Long permissionId = getOrCreateCancelPermission(actorId);

        List<Long> txIds = jdbc.queryForList(
                """
                select distinct st.transaction_id
                from sales_transaction st
                left join course c on c.course_id = st.course_id
                where (st.receipt_id = ? and st.receipt_link_status = 'VERIFIED')
                   or (st.receipt_id is null and c.receipt_id = ? and st.receipt_link_status = 'VERIFIED')
                """,
                Long.class, receiptId, receiptId
        );

        for (Long txId : txIds) {
            List<Long> paymentIds = jdbc.queryForList(
                    "select payment_id from payment_transaction where transaction_id = ? and payment_status = true",
                    Long.class, txId
            );

            for (Long pId : paymentIds) {
                jdbc.update(
                        """
                        insert into transaction_cancellation (
                            permission_id, transaction_id, payment_id, reason, cancelled_by, cancelled_at
                        ) values (?, ?, ?, ?, ?, current_timestamp)
                        """,
                        permissionId, txId, pId, reason, actorId
                );
                jdbc.update(
                        """
                        update payment_transaction set payment_status = false,
                               payment_update_at = current_timestamp, payment_update_by = ?
                        where payment_id = ?
                        """,
                        actorId, pId
                );
            }

            jdbc.update(
                    """
                    update sales_transaction set transaction_status = false,
                           transaction_update_at = current_timestamp, transaction_update_by = ?
                    where transaction_id = ?
                    """,
                    actorId, txId
            );
        }

        List<Long> courseIds = jdbc.queryForList(
                """
                select c.course_id from course c
                where c.receipt_id = ? and c.course_status = 'Active'
                  and not exists (
                      select 1 from sales_transaction st
                      where st.course_id = c.course_id
                        and st.receipt_id is not null
                        and (st.receipt_id <> c.receipt_id or st.receipt_link_status <> 'VERIFIED')
                  )
                """,
                Long.class, receiptId
        );
        for (Long cId : courseIds) {
            Integer remaining = jdbc.queryForObject(
                    "select remaining_visits from course where course_id = ?",
                    Integer.class, cId
            );
            if (remaining == null) remaining = 0;

            jdbc.update(
                    """
                    update course set course_status = 'Cancelled',
                           remaining_visits = 0,
                           course_update_at = current_timestamp, course_update_by = ?
                    where course_id = ?
                    """,
                    actorId, cId
            );

            jdbc.update(
                    """
                    insert into course_ledger (
                        course_id, patient_id, entry_type, qty_change, balance_after,
                        reference_type, reference_id, remark, ledger_create_at, ledger_create_by
                    ) values (
                        ?, (select patient_id from receipt where receipt_id = ?), 'Adjustment', ?, 0,
                        'RECEIPT_VOID', ?, ?, current_timestamp, ?
                    )
                    """,
                    cId, receiptId, -remaining, receiptId, "Void receipt: " + reason, actorId
            );
        }
    }

    public boolean isVoided(Long receiptId) {
        Integer count = jdbc.queryForObject(
                """
                select count(*)
                from transaction_cancellation tc
                join sales_transaction st on st.transaction_id = tc.transaction_id
                left join course c on c.course_id = st.course_id
                where (st.receipt_id = ? and st.receipt_link_status = 'VERIFIED')
                   or (st.receipt_id is null and c.receipt_id = ? and st.receipt_link_status = 'VERIFIED')
                """,
                Integer.class, receiptId, receiptId
        );
        return count != null && count > 0;
    }

    public Long getOrCreateCancelPermission(Long actorId) {
        List<Long> ids = jdbc.queryForList(
                "select permission_id from permission where permission_name = 'TRANSACTION_CANCEL'",
                Long.class
        );
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        return jdbc.queryForObject(
                """
                insert into permission (permission_name, module_name, permission_create_at, permission_create_by)
                values ('TRANSACTION_CANCEL', 'Billing', current_timestamp, ?)
                returning permission_id
                """,
                Long.class, actorId
        );
    }

    public Long getOrCreateDefaultCommissionScheme(Long actorId) {
        List<Long> ids = jdbc.queryForList(
                "select commission_scheme_id from commission_scheme limit 1",
                Long.class
        );
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        Long permId = getOrCreateCancelPermission(actorId);
        return jdbc.queryForObject(
                """
                insert into commission_scheme (
                    scheme_name, scheme_version, permission_id, effective_from,
                    scheme_create_at, scheme_create_by
                ) values (
                    'Default Scheme', 1, ?, '2026-01-01',
                    current_timestamp, ?
                ) returning commission_scheme_id
                """,
                Long.class, permId, actorId
        );
    }
}
