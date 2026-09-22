package com.sabyeguyz.clinic.databasev2.course;

import com.sabyeguyz.clinic.course.CourseBalanceException;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2ConflictException;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2NotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-v2")
public class DatabaseV2CourseRepository {

    public record CoursePurchaseRecord(
            Long id,
            String courseNo,
            int sequence,
            String courseName,
            BigDecimal price,
            int totalVisits,
            int bonusVisits,
            LocalDate expiryDate,
            String status,
            Long serviceId,
            String serviceCode,
            Long receiptId,
            String receiptNo,
            Long patientId,
            String patientHn,
            String patientFullName,
            Long branchId,
            String branchCode,
            Long sellerStaffId,
            Long caseOwnerStaffId,
            int visitsUsed,
            int remainingVisits,
            String commissionStatus,
            BigDecimal lockedCommissionRate,
            BigDecimal totalCourseCommissionPool,
            BigDecimal commissionAllocationPerVisit,
            LocalDateTime createdAt
    ) {}

    public record CourseMovementRecord(
            Long id,
            Long courseId,
            Long patientId,
            String entryType,
            int qtyChange,
            int balanceAfter,
            String referenceType,
            Long referenceId,
            String remark,
            LocalDateTime createdAt
    ) {}

    public record CourseTransferRecord(
            Long id,
            Long sourcePurchaseId,
            Long destinationPurchaseId,
            Long sourcePatientId,
            String sourcePatientHn,
            String sourcePatientName,
            Long destinationPatientId,
            String destinationPatientHn,
            String destinationPatientName,
            Long courseId,
            String branchCode,
            int quantity,
            Long actorId,
            String reason,
            LocalDateTime createdAt
    ) {}

    private final JdbcTemplate jdbc;

    private static final String SELECT_COURSE = """
            select c.course_id, c.course_no, c.course_sequence, c.course_name, c.course_price,
                   c.total_visits, coalesce(c.bonus_visits, 0) as bonus_visits, c.expiry_date,
                   cast(c.course_status as varchar) as status,
                   c.service_id, s.service_code,
                   c.receipt_id, r.receipt_no,
                   p.patient_id, p.hn,
                   concat(p.patient_fname, case when p.patient_lname is not null and p.patient_lname <> '-' then concat(' ', p.patient_lname) else '' end) as patient_name,
                   b.branch_id, b.branch_code,
                   c.seller_staff_id, c.case_owner_staff_id,
                   c.visits_used, c.remaining_visits,
                   cast(c.commission_status as varchar) as commission_status,
                   c.locked_commission_rate, c.total_course_commission_pool, c.commission_allocation_per_visit,
                   c.course_create_at
            from course c
            join receipt r on r.receipt_id = c.receipt_id
            left join course_shared_member csm on csm.course_id = c.course_id and csm.member_role = 'Owner'
            join patient p on p.patient_id = coalesce(csm.patient_id, r.patient_id)
            join branch b on b.branch_id = r.branch_id
            join service s on s.service_id = c.service_id
            """;

    private static final RowMapper<CoursePurchaseRecord> ROW_MAPPER = (rs, rowNum) -> new CoursePurchaseRecord(
            rs.getLong("course_id"),
            rs.getString("course_no"),
            rs.getInt("course_sequence"),
            rs.getString("course_name"),
            rs.getBigDecimal("course_price"),
            rs.getInt("total_visits"),
            rs.getInt("bonus_visits"),
            rs.getObject("expiry_date", LocalDate.class),
            rs.getString("status"),
            rs.getLong("service_id"),
            rs.getString("service_code"),
            rs.getLong("receipt_id"),
            rs.getString("receipt_no"),
            rs.getLong("patient_id"),
            rs.getString("hn"),
            rs.getString("patient_name"),
            rs.getLong("branch_id"),
            rs.getString("branch_code"),
            rs.getLong("seller_staff_id"),
            rs.getLong("case_owner_staff_id"),
            rs.getInt("visits_used"),
            rs.getInt("remaining_visits"),
            rs.getString("commission_status"),
            rs.getBigDecimal("locked_commission_rate"),
            rs.getBigDecimal("total_course_commission_pool"),
            rs.getBigDecimal("commission_allocation_per_visit"),
            rs.getObject("course_create_at", LocalDateTime.class)
    );

    public DatabaseV2CourseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CoursePurchaseRecord> findById(Long id) {
        return jdbc.query(SELECT_COURSE + " where c.course_id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    public Optional<CoursePurchaseRecord> findByIdForUpdate(Long id) {
        return jdbc.query(SELECT_COURSE + " where c.course_id = ? for update of c", ROW_MAPPER, id).stream().findFirst();
    }

    public List<CoursePurchaseRecord> findByPatientId(Long patientId) {
        String sql = SELECT_COURSE + """
                where p.patient_id = ?
                   or c.course_id in (select csm.course_id from course_shared_member csm where csm.patient_id = ? and csm.member_status = true)
                order by c.course_id desc
                """;
        return jdbc.query(sql, ROW_MAPPER, patientId, patientId);
    }

    public CoursePurchaseRecord purchase(
            Long patientId, Long serviceId, Long branchId, String branchCode,
            Long sellerStaffId, Long actorId
    ) {
        Long nextVal = jdbc.queryForObject("select nextval('receipt_receipt_id_seq')", Long.class);
        String receiptNo = String.format("RE-%07d", nextVal);
        LocalDate today = LocalDate.now();

        // 1. Insert receipt
        jdbc.update(
                """
                insert into receipt (
                    receipt_id, receipt_no, patient_id, branch_id, sale_date,
                    receipt_create_at, receipt_create_by
                ) values (?, ?, ?, ?, ?, current_timestamp, ?)
                """,
                nextVal, receiptNo, patientId, branchId, today, actorId
        );

        // 2. Fetch service properties
        var serviceProps = jdbc.queryForMap(
                "select service_name, service_price, default_total_visits, default_bonus_visits, default_validity_days from service where service_id = ?",
                serviceId
        );

        String name = (String) serviceProps.get("service_name");
        BigDecimal price = (BigDecimal) serviceProps.get("service_price");
        Integer totalVisits = (Integer) serviceProps.get("default_total_visits");
        if (totalVisits == null || totalVisits <= 0) totalVisits = 10;
        Integer bonusVisits = (Integer) serviceProps.get("default_bonus_visits");
        if (bonusVisits == null) bonusVisits = 0;
        Integer validityDays = (Integer) serviceProps.get("default_validity_days");
        if (validityDays == null || validityDays <= 0) validityDays = 365;

        LocalDate expiryDate = today.plusDays(validityDays);
        String saleMonth = String.format("%04d-%02d", today.getYear(), today.getMonthValue());
        int remaining = totalVisits + bonusVisits;
        Long schemeId = getOrCreateDefaultCommissionScheme(actorId);

        Long sellerId = sellerStaffId;
        if (sellerId == null) {
            List<Long> staffIds = jdbc.query(
                    "select staff_id from staff where branch_id = ? and staff_status = true order by staff_id limit 1",
                    (rs, rowNum) -> rs.getLong(1),
                    branchId
            );
            if (staffIds.isEmpty()) {
                staffIds = jdbc.query(
                        "select staff_id from staff where staff_status = true order by staff_id limit 1",
                        (rs, rowNum) -> rs.getLong(1)
                );
            }
            if (!staffIds.isEmpty()) {
                sellerId = staffIds.get(0);
            }
        }

        // 3. Insert course
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
                receiptNo, name, price,
                totalVisits, bonusVisits, expiryDate,
                serviceId, nextVal, saleMonth, sellerId, sellerId,
                schemeId, remaining, actorId
        );

        // 4. Insert shared member
        jdbc.update(
                "insert into course_shared_member (course_id, patient_id, member_role, member_status) values (?, ?, 'Owner', true)",
                courseId, patientId
        );

        // 5. Insert course ledger
        jdbc.update(
                """
                insert into course_ledger (
                    course_id, patient_id, entry_type, qty_change, balance_after,
                    reference_type, reference_id, remark, ledger_create_at, ledger_create_by
                ) values (
                    ?, ?, 'Purchase', ?, ?,
                    'RECEIPT', ?, 'Course purchase', current_timestamp, ?
                )
                """,
                courseId, patientId, remaining, remaining, nextVal, actorId
        );

        // 6. Insert sales transaction
        jdbc.update(
                """
                insert into sales_transaction (
                    patient_id, branch_id, service_id, transaction_type,
                    salesperson_staff_id, quantity, unit_price, amount,
                    transaction_date, course_id, transaction_status,
                    transaction_create_at, transaction_create_by
                ) values (
                    ?, ?, ?, 'CoursePurchase',
                    ?, 1, ?, ?,
                    current_timestamp, ?, true,
                    current_timestamp, ?
                )
                """,
                patientId, branchId, serviceId, sellerId, price, price, courseId, actorId
        );

        return findById(courseId).orElseThrow();
    }

    public synchronized CoursePurchaseRecord consumeSession(Long courseId, Long appointmentId, String note, Long treatingStaffId, Long actorId) {
        CoursePurchaseRecord course = findById(courseId)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบคอร์ส id " + courseId));

        if (!"Active".equals(course.status())) {
            throw new CourseBalanceException("คอร์สไม่อยู่ในสถานะใช้งาน (" + course.status() + ")");
        }
        if (course.expiryDate().isBefore(LocalDate.now())) {
            throw new CourseBalanceException("คอร์สหมดอายุแล้ว");
        }

        List<Integer> list = jdbc.queryForList("""
                update course set
                    remaining_visits = remaining_visits - 1,
                    visits_used = visits_used + 1,
                    course_update_at = current_timestamp,
                    course_update_by = ?
                where course_id = ? and remaining_visits > 0 and course_status = 'Active'
                returning remaining_visits
                """, Integer.class, actorId, courseId);
        if (list.isEmpty()) {
            throw new CourseBalanceException("ยอดคงเหลือในคอร์สหมดแล้ว");
        }
        int newRemaining = list.get(0);

        Long ledgerId = jdbc.queryForObject(
                """
                insert into course_ledger (
                    course_id, patient_id, entry_type, qty_change, balance_after,
                    reference_type, reference_id, remark, ledger_create_at, ledger_create_by
                ) values (
                    ?, ?, 'Treatment', -1, ?,
                    'APPOINTMENT', ?, ?, current_timestamp, ?
                ) returning ledger_id
                """,
                Long.class,
                courseId, course.patientId(), newRemaining, appointmentId, note, actorId
        );

        // Record course_visit_usage if visit exists or create one
        Long visitId = null;
        if (appointmentId != null) {
            List<Long> visits = jdbc.queryForList("select visit_id from patient_visit where appointment_id = ?", Long.class, appointmentId);
            if (!visits.isEmpty()) {
                visitId = visits.get(0);
            } else {
                visitId = jdbc.queryForObject(
                        """
                        insert into patient_visit (
                            appointment_id, patient_id, branch_id, visit_date, visit_status, visit_create_at, visit_create_by
                        ) values (
                            ?, ?, ?, current_timestamp, 'Arrived', current_timestamp, ?
                        ) returning visit_id
                        """,
                        Long.class, appointmentId, course.patientId(), course.branchId(), actorId
                );
            }
        } else {
            visitId = jdbc.queryForObject(
                    """
                    insert into patient_visit (
                        patient_id, branch_id, visit_date, visit_status, visit_create_at, visit_create_by
                    ) values (
                        ?, ?, current_timestamp, 'Completed', current_timestamp, ?
                    ) returning visit_id
                    """,
                    Long.class, course.patientId(), course.branchId(), actorId
            );
        }

        Long treatingId = treatingStaffId;
        if (treatingId == null) {
            treatingId = course.caseOwnerStaffId() != null ? course.caseOwnerStaffId() : course.sellerStaffId();
        }
        if (treatingId == null) {
            List<Long> staffIds = jdbc.query(
                    "select staff_id from staff where branch_id = ? and staff_status = true order by staff_id limit 1",
                    (rs, rowNum) -> rs.getLong(1),
                    course.branchId()
            );
            if (staffIds.isEmpty()) {
                staffIds = jdbc.query(
                        "select staff_id from staff where staff_status = true order by staff_id limit 1",
                        (rs, rowNum) -> rs.getLong(1)
                );
            }
            if (!staffIds.isEmpty()) {
                treatingId = staffIds.get(0);
            }
        }
        Long ownerId = course.caseOwnerStaffId() != null ? course.caseOwnerStaffId() : treatingId;

        jdbc.update(
                """
                insert into course_visit_usage (
                    visit_id, course_id, patient_id, treating_staff_id, case_owner_staff_id,
                    visit_date, visit_qty, commission_allocation_per_visit, gross_commission_allocation,
                    substitute_treatment_fee_amount, owner_net_commission_released,
                    usage_create_at, usage_create_by
                ) values (
                    ?, ?, ?, ?, ?,
                    current_date, 1, 0, 0,
                    0, 0,
                    current_timestamp, ?
                )
                """,
                visitId, courseId, course.patientId(), treatingId, ownerId, actorId
        );

        return findById(courseId).orElseThrow();
    }

    public CoursePurchaseRecord reverseUsage(Long movementId, String reason, Long actorId) {
        List<CourseMovementRecord> movements = jdbc.query(
                "select * from course_ledger where ledger_id = ?",
                (rs, rowNum) -> new CourseMovementRecord(
                        rs.getLong("ledger_id"),
                        rs.getLong("course_id"),
                        rs.getLong("patient_id"),
                        rs.getString("entry_type"),
                        rs.getInt("qty_change"),
                        rs.getInt("balance_after"),
                        rs.getString("reference_type"),
                        (Long) rs.getObject("reference_id"),
                        rs.getString("remark"),
                        rs.getObject("ledger_create_at", LocalDateTime.class)
                ),
                movementId
        );

        if (movements.isEmpty()) {
            throw new DatabaseV2NotFoundException("ไม่พบรายการเคลื่อนไหว id " + movementId);
        }

        CourseMovementRecord orig = movements.get(0);
        if (!"Treatment".equals(orig.entryType())) {
            throw new DatabaseV2ConflictException("รายการนี้ไม่ใช่รายการใช้งานคอร์ส");
        }

        // Check if already reversed
        Integer revCount = jdbc.queryForObject(
                "select count(*) from course_ledger where reference_type = 'MOVEMENT_REVERSE' and reference_id = ?",
                Integer.class, movementId
        );
        if (revCount != null && revCount > 0) {
            throw new DatabaseV2ConflictException("รายการนี้ถูกยกเลิกไปแล้ว");
        }

        CoursePurchaseRecord course = findByIdForUpdate(orig.courseId())
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบคอร์ส id " + orig.courseId()));

        int newRemaining = course.remainingVisits() + 1;
        int newUsed = Math.max(0, course.visitsUsed() - 1);

        jdbc.update(
                """
                update course set
                    remaining_visits = ?,
                    visits_used = ?,
                    course_update_at = current_timestamp,
                    course_update_by = ?
                where course_id = ?
                """,
                newRemaining, newUsed, actorId, course.id()
        );

        jdbc.update(
                """
                insert into course_ledger (
                    course_id, patient_id, entry_type, qty_change, balance_after,
                    reference_type, reference_id, remark, ledger_create_at, ledger_create_by
                ) values (
                    ?, ?, 'Adjustment', 1, ?,
                    'MOVEMENT_REVERSE', ?, ?, current_timestamp, ?
                )
                """,
                course.id(), orig.patientId(), newRemaining, movementId, "Reverse usage: " + reason, actorId
        );

        return findById(course.id()).orElseThrow();
    }

    public CoursePurchaseRecord cancel(Long courseId, String reason, Long actorId) {
        CoursePurchaseRecord course = findByIdForUpdate(courseId)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบคอร์ส id " + courseId));

        if ("Cancelled".equals(course.status())) {
            throw new DatabaseV2ConflictException("คอร์สนี้ถูกยกเลิกไปแล้ว");
        }

        int remaining = course.remainingVisits();

        jdbc.update(
                """
                update course set
                    course_status = 'Cancelled',
                    remaining_visits = 0,
                    course_update_at = current_timestamp,
                    course_update_by = ?
                where course_id = ?
                """,
                actorId, courseId
        );

        jdbc.update(
                """
                insert into course_ledger (
                    course_id, patient_id, entry_type, qty_change, balance_after,
                    reference_type, reference_id, remark, ledger_create_at, ledger_create_by
                ) values (
                    ?, ?, 'Adjustment', ?, 0,
                    'COURSE_CANCEL', ?, ?, current_timestamp, ?
                )
                """,
                courseId, course.patientId(), -remaining, courseId, "Cancel course: " + reason, actorId
        );

        return findById(courseId).orElseThrow();
    }

    public List<CourseMovementRecord> findMovements(Long courseId) {
        return jdbc.query(
                "select * from course_ledger where course_id = ? order by ledger_id asc",
                (rs, rowNum) -> new CourseMovementRecord(
                        rs.getLong("ledger_id"),
                        rs.getLong("course_id"),
                        rs.getLong("patient_id"),
                        rs.getString("entry_type"),
                        rs.getInt("qty_change"),
                        rs.getInt("balance_after"),
                        rs.getString("reference_type"),
                        (Long) rs.getObject("reference_id"),
                        rs.getString("remark"),
                        rs.getObject("ledger_create_at", LocalDateTime.class)
                ),
                courseId
        );
    }

    public CourseTransferRecord transfer(Long sourcePurchaseId, Long destinationPatientId, int quantity, String reason, Long actorId) {
        CoursePurchaseRecord source = findByIdForUpdate(sourcePurchaseId)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบคอร์สต้นทาง id " + sourcePurchaseId));

        if (!"Active".equals(source.status()) || source.expiryDate().isBefore(LocalDate.now())) {
            throw new CourseBalanceException("คอร์สต้นทางไม่อยู่ในสถานะใช้งานหรือหมดอายุแล้ว");
        }
        if (source.patientId().equals(destinationPatientId)) {
            throw new IllegalArgumentException("Destination must be a different patient");
        }
        if (source.remainingVisits() < quantity) {
            throw new CourseBalanceException("จำนวนครั้งคงเหลือไม่พอสำหรับการโอน");
        }

        // Fetch destination patient
        var destPatient = jdbc.queryForMap("select hn, patient_fname, patient_lname from patient where patient_id = ?", destinationPatientId);
        String destHn = (String) destPatient.get("hn");
        String destFname = (String) destPatient.get("patient_fname");
        String destLname = (String) destPatient.get("patient_lname");
        String destFullName = destFname + (destLname != null && !"-".equals(destLname) ? " " + destLname : "");

        int nextSeq = source.sequence() + 1;
        String destCourseNo = String.format("%s-%02d", source.receiptNo(), nextSeq);

        Long schemeId = getOrCreateDefaultCommissionScheme(actorId);

        // 1. Create destination course lot
        Long destCourseId = jdbc.queryForObject(
                """
                insert into course (
                    course_no, course_sequence, course_name, course_price,
                    total_visits, bonus_visits, expiry_date, course_status,
                    service_id, receipt_id, sale_month, seller_staff_id, case_owner_staff_id,
                    commission_status, commission_scheme_id, commission_scheme_version,
                    visits_used, remaining_visits, course_create_at, course_create_by
                ) values (
                    ?, ?, ?, 0,
                    ?, 0, ?, 'Active',
                    ?, ?, (select sale_month from course where course_id = ?), ?, ?,
                    'Provisional', ?, 1,
                    0, ?, current_timestamp, ?
                ) returning course_id
                """,
                Long.class,
                destCourseNo, nextSeq, source.courseName(),
                quantity, source.expiryDate(),
                source.serviceId(), source.receiptId(), sourcePurchaseId,
                source.sellerStaffId(), source.caseOwnerStaffId(),
                schemeId, quantity, actorId
        );

        // 2. Add destination shared member
        jdbc.update(
                "insert into course_shared_member (course_id, patient_id, member_role, member_status) values (?, ?, 'Owner', true)",
                destCourseId, destinationPatientId
        );

        // 3. Record course_transfer
        Long transferId = jdbc.queryForObject(
                """
                insert into course_transfer (
                    course_id, from_patient_id, to_patient_id, qty_transferred,
                    transfer_date, remark, transfer_create_at, transfer_create_by
                ) values (
                    ?, ?, ?, ?,
                    current_timestamp, ?, current_timestamp, ?
                ) returning transfer_id
                """,
                Long.class,
                sourcePurchaseId, source.patientId(), destinationPatientId, quantity, reason, actorId
        );

        // 4. Update source remaining
        int newSourceRemaining = source.remainingVisits() - quantity;
        jdbc.update(
                "update course set remaining_visits = ?, course_update_at = current_timestamp, course_update_by = ? where course_id = ?",
                newSourceRemaining, actorId, sourcePurchaseId
        );

        // 5. Course ledger for source (TransferOut)
        jdbc.update(
                """
                insert into course_ledger (
                    course_id, patient_id, entry_type, qty_change, balance_after,
                    reference_type, reference_id, remark, ledger_create_at, ledger_create_by
                ) values (
                    ?, ?, 'TransferOut', ?, ?,
                    'TRANSFER', ?, ?, current_timestamp, ?
                )
                """,
                sourcePurchaseId, source.patientId(), -quantity, newSourceRemaining, transferId, reason, actorId
        );

        // 6. Course ledger for destination (TransferIn)
        jdbc.update(
                """
                insert into course_ledger (
                    course_id, patient_id, entry_type, qty_change, balance_after,
                    reference_type, reference_id, remark, ledger_create_at, ledger_create_by
                ) values (
                    ?, ?, 'TransferIn', ?, ?,
                    'TRANSFER', ?, ?, current_timestamp, ?
                )
                """,
                destCourseId, destinationPatientId, quantity, quantity, transferId, reason, actorId
        );

        return new CourseTransferRecord(
                transferId,
                sourcePurchaseId,
                destCourseId,
                source.patientId(),
                source.patientHn(),
                source.patientFullName(),
                destinationPatientId,
                destHn,
                destFullName,
                source.serviceId(),
                source.branchCode(),
                quantity,
                actorId,
                reason,
                LocalDateTime.now()
        );
    }

    public List<CourseTransferRecord> findTransferHistory(Long patientId) {
        StringBuilder sql = new StringBuilder("""
                select ct.transfer_id, ct.course_id, ct.from_patient_id, ct.to_patient_id,
                       ct.qty_transferred, ct.remark, ct.transfer_create_at, ct.transfer_create_by,
                       p1.hn as from_hn, concat(p1.patient_fname, ' ', p1.patient_lname) as from_name,
                       p2.hn as to_hn, concat(p2.patient_fname, ' ', p2.patient_lname) as to_name,
                       c.service_id, b.branch_code,
                       (select c2.course_id from course c2 where c2.receipt_id = c.receipt_id and c2.course_id <> c.course_id order by c2.course_id desc limit 1) as dest_course_id
                from course_transfer ct
                join course c on c.course_id = ct.course_id
                join receipt r on r.receipt_id = c.receipt_id
                join branch b on b.branch_id = r.branch_id
                join patient p1 on p1.patient_id = ct.from_patient_id
                join patient p2 on p2.patient_id = ct.to_patient_id
                """);

        List<Object> params = new ArrayList<>();
        if (patientId != null) {
            sql.append(" where ct.from_patient_id = ? or ct.to_patient_id = ?");
            params.add(patientId);
            params.add(patientId);
        }

        sql.append(" order by ct.transfer_id desc");

        return jdbc.query(sql.toString(), (rs, rowNum) -> new CourseTransferRecord(
                rs.getLong("transfer_id"),
                rs.getLong("course_id"),
                (Long) rs.getObject("dest_course_id"),
                rs.getLong("from_patient_id"),
                rs.getString("from_hn"),
                rs.getString("from_name"),
                rs.getLong("to_patient_id"),
                rs.getString("to_hn"),
                rs.getString("to_name"),
                rs.getLong("service_id"),
                rs.getString("branch_code"),
                rs.getInt("qty_transferred"),
                rs.getLong("transfer_create_by"),
                rs.getString("remark"),
                rs.getObject("transfer_create_at", LocalDateTime.class)
        ), params.toArray());
    }

    public Long getOrCreateDefaultCommissionScheme(Long actorId) {
        List<Long> ids = jdbc.queryForList("select commission_scheme_id from commission_scheme limit 1", Long.class);
        if (!ids.isEmpty()) return ids.get(0);

        List<Long> pids = jdbc.queryForList("select permission_id from permission where permission_name = 'COMMISSION_SCHEME_MANAGE'", Long.class);
        Long permId;
        if (!pids.isEmpty()) {
            permId = pids.get(0);
        } else {
            permId = jdbc.queryForObject(
                    "insert into permission (permission_name, module_name, permission_create_at, permission_create_by) values ('COMMISSION_SCHEME_MANAGE', 'Commission', current_timestamp, ?) returning permission_id",
                    Long.class, actorId
            );
        }

        return jdbc.queryForObject(
                """
                insert into commission_scheme (
                    scheme_name, scheme_version, permission_id, effective_from,
                    scheme_create_at, scheme_create_by
                ) values ('Default Scheme', 1, ?, '2026-01-01', current_timestamp, ?)
                returning commission_scheme_id
                """,
                Long.class, permId, actorId
        );
    }
}
