package com.sabyeguyz.clinic.databasev2.bootstrap;

import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("database-v2")
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
public class DatabaseV2BootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseV2BootstrapRunner.class);

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${app.bootstrap.default-branch-code:01}")
    private String defaultBranchCode = "01";

    @Value("${app.bootstrap.default-branch-name:Main Branch}")
    private String defaultBranchName = "Main Branch";

    @Value("${app.bootstrap.admin-username:admin}")
    private String adminUsername = "admin";

    @Value("${app.bootstrap.admin-password:Admin@123456}")
    private String adminPassword = "Admin@123456";

    public DatabaseV2BootstrapRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Running opt-in Database v2 bootstrap runner...");
        bootstrap();
        log.info("Database v2 bootstrap completed successfully.");
    }

    public void bootstrap() {
        // 1. Seed or resolve Admin User
        Long adminUserId = getOrCreateAdminUser(adminUsername, adminPassword);

        // 2. Seed Default Branch
        Long branchId = getOrCreateBranch(defaultBranchCode, defaultBranchName, adminUserId);

        // 3. Seed Roles (Manager, Therapist, Receptionist)
        Long managerRoleId = getOrCreateRole("Manager", adminUserId);
        getOrCreateRole("Therapist", adminUserId);
        getOrCreateRole("Receptionist", adminUserId);

        // 4. Seed Initial Staff (Manager and Therapist positions)
        Long staffId = getOrCreateStaff("ST-MGR-01", "Admin", "Manager", "Manager", branchId, adminUserId);
        getOrCreateStaff("ST-TH-01", "Primary", "Therapist", "Therapist", branchId, adminUserId);

        // 5. Link Admin User to Staff if not linked
        jdbc.update("update app_user set staff_id = ? where user_id = ? and staff_id is null", staffId, adminUserId);

        // 6. Assign Manager role and Branch access to Admin User
        jdbc.update(
                "insert into user_role (user_id, role_id) values (?, ?) on conflict do nothing",
                adminUserId, managerRoleId
        );
        jdbc.update(
                "insert into user_branch_access (user_id, branch_id) values (?, ?) on conflict do nothing",
                adminUserId, branchId
        );

        // 7. Seed Payment Methods
        getOrCreatePaymentMethod("Cash", adminUserId);
        getOrCreatePaymentMethod("CreditCard", adminUserId);
        getOrCreatePaymentMethod("PromptPay", adminUserId);

        // 8. Seed Room
        getOrCreateRoom(branchId, "Treatment Room 1", "PhysicalTherapy", adminUserId);

        // 9. Seed Master Data Types & Values
        Long symptomTypeId = getOrCreateMasterDataType("Symptoms", adminUserId);
        getOrCreateMasterDataValue(symptomTypeId, "Office Syndrome", 1, adminUserId);
        getOrCreateMasterDataValue(symptomTypeId, "Neck & Shoulder Pain", 2, adminUserId);
        getOrCreateMasterDataValue(symptomTypeId, "Lower Back Pain", 3, adminUserId);

        Long techniqueTypeId = getOrCreateMasterDataType("TreatmentTechniques", adminUserId);
        getOrCreateMasterDataValue(techniqueTypeId, "Manual Therapy", 1, adminUserId);
        getOrCreateMasterDataValue(techniqueTypeId, "Acupuncture", 2, adminUserId);
        getOrCreateMasterDataValue(techniqueTypeId, "Ultrasound", 3, adminUserId);

        // 10. Seed Services
        getOrCreateService("SRV-ASSESS", "Physical Assessment", "Assessment", new BigDecimal("500.00"), null, null, null, adminUserId);
        getOrCreateService("SRV-SINGLE", "Single Visit Physical Therapy", "SingleVisit", new BigDecimal("1200.00"), null, null, null, adminUserId);
        getOrCreateService("CRS-BASIC-10", "Physical Therapy 10-Session Course", "Course", new BigDecimal("10000.00"), 10, 0, 365, adminUserId);

        // 11. Seed Commission Permission & Scheme
        Long permId = getOrCreatePermission("COMMISSION_MANAGE", "Commission", adminUserId);
        getOrCreateCommissionScheme("Default Scheme", 1, permId, "2026-01-01", adminUserId);
    }

    private void syncSequence(String seqName, String tableName, String idCol) {
        try {
            jdbc.execute("select setval('" + seqName + "', coalesce((select max(" + idCol + ") from " + tableName + "), 0) + 1, false)");
        } catch (Exception ignored) {
        }
    }

    private Long getOrCreateAdminUser(String username, String password) {
        List<Long> ids = jdbc.queryForList("select user_id from app_user where username = ?", Long.class, username);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        syncSequence("app_user_user_id_seq", "app_user", "user_id");
        String hash = passwordEncoder.encode(password);
        return jdbc.queryForObject(
                """
                insert into app_user (username, password_hash, user_status, last_login_at, user_create_at)
                values (?, ?, true, current_timestamp, current_timestamp)
                returning user_id
                """,
                Long.class, username, hash
        );
    }

    private Long getOrCreateBranch(String code, String name, Long creatorId) {
        List<Long> ids = jdbc.queryForList("select branch_id from branch where branch_code = ?", Long.class, code);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        syncSequence("branch_branch_id_seq", "branch", "branch_id");
        return jdbc.queryForObject(
                """
                insert into branch (branch_code, branch_name, address, phone, branch_status, branch_create_by)
                values (?, ?, '123 Main Clinic St', '020000001', true, ?)
                returning branch_id
                """,
                Long.class, code, name, creatorId
        );
    }

    private Long getOrCreateRole(String roleName, Long creatorId) {
        List<Long> ids = jdbc.queryForList("select role_id from role where role_name = ?", Long.class, roleName);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        syncSequence("role_role_id_seq", "role", "role_id");
        return jdbc.queryForObject(
                """
                insert into role (role_name, role_create_by)
                values (?, ?)
                returning role_id
                """,
                Long.class, roleName, creatorId
        );
    }

    private Long getOrCreateStaff(String code, String fname, String lname, String pos, Long branchId, Long creatorId) {
        List<Long> ids = jdbc.queryForList("select staff_id from staff where staff_code = ?", Long.class, code);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        syncSequence("staff_staff_id_seq", "staff", "staff_id");
        return jdbc.queryForObject(
                """
                insert into staff (staff_code, staff_fname, staff_lname, position, branch_id, staff_status, hire_date, staff_create_by)
                values (?, ?, ?, cast(? as staff_position_enum), ?, true, '2026-01-01', ?)
                returning staff_id
                """,
                Long.class, code, fname, lname, pos, branchId, creatorId
        );
    }

    private Long getOrCreatePaymentMethod(String name, Long creatorId) {
        List<Long> ids = jdbc.queryForList("select payment_method_id from payment_method where payment_method_name = ?", Long.class, name);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        syncSequence("payment_method_payment_method_id_seq", "payment_method", "payment_method_id");
        return jdbc.queryForObject(
                """
                insert into payment_method (payment_method_name, payment_method_status, payment_method_create_by)
                values (?, true, ?)
                returning payment_method_id
                """,
                Long.class, name, creatorId
        );
    }

    private Long getOrCreateRoom(Long branchId, String roomName, String roomType, Long creatorId) {
        List<Long> ids = jdbc.queryForList("select room_id from room where branch_id = ? and room_name = ?", Long.class, branchId, roomName);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        syncSequence("room_room_id_seq", "room", "room_id");
        return jdbc.queryForObject(
                """
                insert into room (branch_id, room_name, room_type, room_status, room_create_by)
                values (?, ?, ?, true, ?)
                returning room_id
                """,
                Long.class, branchId, roomName, roomType, creatorId
        );
    }

    private Long getOrCreateMasterDataType(String typeName, Long creatorId) {
        List<Long> ids = jdbc.queryForList("select type_id from master_data_type where type_name = ?", Long.class, typeName);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        syncSequence("master_data_type_type_id_seq", "master_data_type", "type_id");
        return jdbc.queryForObject(
                """
                insert into master_data_type (type_name, type_create_by)
                values (?, ?)
                returning type_id
                """,
                Long.class, typeName, creatorId
        );
    }

    private Long getOrCreateMasterDataValue(Long typeId, String valueName, int sortOrder, Long creatorId) {
        List<Long> ids = jdbc.queryForList("select value_id from master_data_value where type_id = ? and value_name = ?", Long.class, typeId, valueName);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        syncSequence("master_data_value_value_id_seq", "master_data_value", "value_id");
        return jdbc.queryForObject(
                """
                insert into master_data_value (type_id, value_name, value_status, sort_order, value_create_by)
                values (?, ?, true, ?, ?)
                returning value_id
                """,
                Long.class, typeId, valueName, sortOrder, creatorId
        );
    }

    private Long getOrCreateService(String code, String name, String type, BigDecimal price, Integer totalVisits, Integer bonusVisits, Integer validityDays, Long creatorId) {
        List<Long> ids = jdbc.queryForList("select service_id from service where service_code = ?", Long.class, code);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        syncSequence("service_service_id_seq", "service", "service_id");
        return jdbc.queryForObject(
                """
                insert into service (service_code, service_name, service_type, service_price, service_status,
                                     default_total_visits, default_bonus_visits, default_validity_days, service_create_by)
                values (?, ?, cast(? as service_type_enum), ?, true, ?, ?, ?, ?)
                returning service_id
                """,
                Long.class, code, name, type, price, totalVisits, bonusVisits, validityDays, creatorId
        );
    }

    private Long getOrCreatePermission(String permName, String moduleName, Long creatorId) {
        List<Long> ids = jdbc.queryForList("select permission_id from permission where permission_name = ?", Long.class, permName);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        syncSequence("permission_permission_id_seq", "permission", "permission_id");
        return jdbc.queryForObject(
                """
                insert into permission (permission_name, module_name, permission_create_by)
                values (?, ?, ?)
                returning permission_id
                """,
                Long.class, permName, moduleName, creatorId
        );
    }

    private Long getOrCreateCommissionScheme(String name, int version, Long permId, String effectiveFrom, Long creatorId) {
        List<Long> ids = jdbc.queryForList("select commission_scheme_id from commission_scheme where scheme_name = ? and scheme_version = ?", Long.class, name, version);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        syncSequence("commission_scheme_commission_scheme_id_seq", "commission_scheme", "commission_scheme_id");
        return jdbc.queryForObject(
                """
                insert into commission_scheme (scheme_name, scheme_version, permission_id, effective_from, scheme_create_by)
                values (?, ?, ?, cast(? as date), ?)
                returning commission_scheme_id
                """,
                Long.class, name, version, permId, effectiveFrom, creatorId
        );
    }
}
