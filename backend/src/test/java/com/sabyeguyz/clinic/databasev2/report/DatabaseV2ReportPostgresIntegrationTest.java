package com.sabyeguyz.clinic.databasev2.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sabyeguyz.clinic.report.AppointmentReportResponse;
import com.sabyeguyz.clinic.report.CourseBalanceReportResponse;
import com.sabyeguyz.clinic.report.DashboardResponse;
import com.sabyeguyz.clinic.report.ReportPeriod;
import com.sabyeguyz.clinic.report.RevenueReportResponse;
import com.sabyeguyz.clinic.report.StaffSalesReportResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DatabaseV2ReportPostgresIntegrationTest {

    private JdbcTemplate jdbc;
    private DatabaseV2ReportRepository repository;
    private static final LocalDate DAY = LocalDate.of(2026, 6, 15);

    @BeforeEach
    void setUp() {
        String host = System.getenv().getOrDefault("POSTGRES_HOST", "localhost");
        String port = System.getenv().getOrDefault("POSTGRES_PORT", "5432");
        String db = System.getenv().getOrDefault("POSTGRES_DB", "clinic_erp");
        String user = System.getenv().getOrDefault("POSTGRES_USER", "clinic");
        String password = System.getenv().getOrDefault("POSTGRES_PASSWORD", "local-verification-password");

        try {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.postgresql.Driver");
            ds.setUrl("jdbc:postgresql://" + host + ":" + port + "/" + db);
            ds.setUsername(user);
            ds.setPassword(password);

            jdbc = new JdbcTemplate(ds);
            jdbc.execute("select 1");
            repository = new DatabaseV2ReportRepository(jdbc);
        } catch (Exception e) {
            Assumptions.abort("PostgreSQL not available on " + host + ":" + port + ": " + e.getMessage());
        }

        seedDependencies();
    }

    private void seedDependencies() {
        jdbc.execute("""
            insert into app_user (user_id, username, password_hash, user_status, last_login_at, user_create_at)
            values (1, 'system_report_test', 'hash', true, current_timestamp, current_timestamp)
            on conflict (user_id) do nothing
        """);

        jdbc.execute("""
            insert into branch (branch_id, branch_code, branch_name, address, phone, branch_status, branch_create_by)
            values (96, '96', 'Report Test Branch', '123 Report St', '020000096', true, 1)
            on conflict (branch_id) do update set branch_code = '96', branch_status = true
        """);

        jdbc.execute("""
            insert into staff (staff_id, staff_code, staff_fname, staff_lname, position, branch_id, staff_status, hire_date, staff_create_by)
            values (96, 'ST-REP-96', 'Alice', 'Smith', 'Therapist', 96, true, '2026-01-01', 1)
            on conflict (staff_id) do update set staff_code = 'ST-REP-96', staff_fname = 'Alice', staff_lname = 'Smith'
        """);

        jdbc.execute("""
            insert into service (service_id, service_code, service_name, service_type, service_price, service_status, service_create_by)
            values (96, 'SRV-REP-96', 'Physical Therapy Course', 'Course', 5000.00, true, 1)
            on conflict (service_id) do nothing
        """);

        jdbc.execute("""
            insert into patient (patient_id, hn, customer_type, title, patient_fname, patient_lname, gender, phone, branch_id, registered_by)
            values (96, '2696099996', 'Thai', 'นาย', 'ReportPatient', 'Tester', 'Male', '0899999996', 96, 1)
            on conflict (patient_id) do nothing
        """);

        jdbc.execute("""
            insert into payment_method (payment_method_id, payment_method_name, payment_method_status, payment_method_create_by)
            values (96, 'CASH_REP', true, 1)
            on conflict (payment_method_id) do nothing
        """);

        // Seed an appointment
        jdbc.execute("""
            insert into appointment (appointment_id, patient_id, staff_id, service_id, branch_id,
                                     appointment_status, appointment_date, appointment_time, appointment_create_by)
            values (96, 96, 96, 96, 96, 'Completed', '2026-06-15', '10:00:00', 1)
            on conflict (appointment_id) do update set appointment_status = 'Completed'
        """);

        // Seed a patient visit
        jdbc.execute("""
            insert into patient_visit (visit_id, appointment_id, patient_id, branch_id, assigned_staff_id,
                                       visit_date, completed_at, visit_status, visit_create_by)
            values (96, 96, 96, 96, 96, '2026-06-15 10:00:00', '2026-06-15 11:00:00', 'Completed', 1)
            on conflict (visit_id) do update set visit_status = 'Completed'
        """);

        jdbc.execute("""
            insert into permission (permission_id, permission_name, module_name, permission_create_by)
            values (96, 'REPORT_PERM', 'Reports', 1)
            on conflict (permission_id) do nothing
        """);

        jdbc.execute("""
            insert into commission_scheme (commission_scheme_id, scheme_name, scheme_version, permission_id, effective_from, scheme_create_by)
            values (96, 'Default Scheme', 1, 96, '2026-01-01', 1)
            on conflict (commission_scheme_id) do nothing
        """);

        // Seed a receipt and course
        jdbc.execute("""
            insert into receipt (receipt_id, receipt_no, patient_id, branch_id, sale_date, receipt_create_by)
            values (96, 'RE-9600001', 96, 96, '2026-06-15', 1)
            on conflict (receipt_id) do update set sale_date = '2026-06-15'
        """);

        jdbc.execute("""
            insert into course (course_id, receipt_id, service_id, course_no, course_sequence, course_name,
                               course_price, total_visits, bonus_visits, remaining_visits, visits_used,
                               expiry_date, course_status, sale_month, seller_staff_id, case_owner_staff_id,
                               commission_status, commission_scheme_id, commission_scheme_version,
                               course_create_at, course_create_by)
            values (96, 96, 96, 'RE-9600001', 1, 'Physical Therapy Course', 5000.00, 5, 0, 5, 0,
                    '2026-12-31', 'Active', '2026-06', 96, 96,
                    'Provisional', 96, 1,
                    current_timestamp, 1)
            on conflict (course_id) do nothing
        """);

        // Seed a sales transaction and payment transaction
        jdbc.execute("""
            insert into sales_transaction (transaction_id, patient_id, branch_id, service_id, course_id,
                                          transaction_type, salesperson_staff_id, quantity, unit_price,
                                          amount, transaction_date, transaction_status, transaction_create_by)
            values (96, 96, 96, 96, 96, 'CoursePurchase', 96, 1, 5000.00, 5000.00, '2026-06-15 10:00:00', true, 1)
            on conflict (transaction_id) do update set transaction_status = true
        """);

        jdbc.execute("""
            insert into payment_transaction (payment_id, patient_id, branch_id, transaction_id,
                                            amount, payment_method_id, payment_date, payment_status, payment_create_by)
            values (96, 96, 96, 96, 5000.00, 96, '2026-06-15 10:00:00', true, 1)
            on conflict (payment_id) do update set payment_status = true
        """);
    }

    @Test
    void resolvesBranchIdSuccessfully() {
        Long id = repository.resolveBranchId("96");
        assertThat(id).isEqualTo(96L);

        assertThatThrownBy(() -> repository.resolveBranchId("ZZ"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dashboardCalculatesAggregatesCorrectly() {
        ReportPeriod period = new ReportPeriod(DAY, DAY);
        DashboardResponse res = repository.dashboard(period, "96");

        assertThat(res.branchCode()).isEqualTo("96");
        assertThat(res.from()).isEqualTo(DAY);
        assertThat(res.to()).isEqualTo(DAY);
        assertThat(res.uniquePatients()).isGreaterThanOrEqualTo(1L);
        assertThat(res.visits()).isGreaterThanOrEqualTo(1L);
        assertThat(res.totalRevenue()).isGreaterThanOrEqualTo(new BigDecimal("5000.00"));
        assertThat(res.courseRevenue()).isGreaterThanOrEqualTo(new BigDecimal("5000.00"));
        assertThat(res.receiptsIssued()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void appointmentReportGeneratesFullDailySeries() {
        ReportPeriod period = new ReportPeriod(DAY, DAY.plusDays(2));
        AppointmentReportResponse res = repository.appointments(period, "96");

        assertThat(res.branchCode()).isEqualTo("96");
        assertThat(res.appointments()).isGreaterThanOrEqualTo(1L);
        assertThat(res.visits()).isGreaterThanOrEqualTo(1L);
        assertThat(res.daily()).hasSize(3);
        assertThat(res.daily().get(0).date()).isEqualTo(DAY);
        assertThat(res.daily().get(0).appointments()).isGreaterThanOrEqualTo(1L);
        assertThat(res.daily().get(1).date()).isEqualTo(DAY.plusDays(1));
        assertThat(res.daily().get(1).appointments()).isEqualTo(0L);
    }

    @Test
    void revenueReportIncludesDailySeriesAndCategories() {
        ReportPeriod period = new ReportPeriod(DAY, DAY.plusDays(2));
        RevenueReportResponse res = repository.revenue(period, "96");

        assertThat(res.branchCode()).isEqualTo("96");
        assertThat(res.totalRevenue()).isGreaterThanOrEqualTo(new BigDecimal("5000.00"));
        assertThat(res.daily()).hasSize(3);
        assertThat(res.daily().get(0).date()).isEqualTo(DAY);
        assertThat(res.daily().get(0).amount()).isGreaterThanOrEqualTo(new BigDecimal("5000.00"));
        assertThat(res.daily().get(1).amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void courseBalancesReportFindsPurchasedCourses() {
        ReportPeriod period = new ReportPeriod(DAY, DAY);
        CourseBalanceReportResponse res = repository.courseBalances(period, "96", 96L);

        assertThat(res.purchases()).isGreaterThanOrEqualTo(1);
        assertThat(res.totalPurchasedSessions()).isGreaterThanOrEqualTo(5L);
        assertThat(res.totalRemainingSessions()).isGreaterThanOrEqualTo(5L);
        assertThat(res.rows()).isNotEmpty();
        assertThat(res.rows().get(0).courseName()).isEqualTo("Physical Therapy Course");
    }

    @Test
    void staffSalesReportTracksAssignedAndUnassigned() {
        ReportPeriod period = new ReportPeriod(DAY, DAY);
        StaffSalesReportResponse res = repository.staffSales(period, "96");

        assertThat(res.branchTotal()).isGreaterThanOrEqualTo(new BigDecimal("5000.00"));
        assertThat(res.rows()).isNotEmpty();
        assertThat(res.rows().get(0).employeeCode()).isEqualTo("ST-REP-96");
        assertThat(res.rows().get(0).amount()).isGreaterThanOrEqualTo(new BigDecimal("5000.00"));
        assertThat(res.unassigned().fullName()).isEqualTo("ไม่ระบุผู้ขาย");
    }
}
