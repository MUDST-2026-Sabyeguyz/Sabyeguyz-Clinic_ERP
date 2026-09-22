package com.sabyeguyz.clinic.databasev2.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DatabaseV2AppointmentPostgresIntegrationTest {

    private JdbcTemplate jdbc;
    private DatabaseV2AppointmentRepository repository;

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
            repository = new DatabaseV2AppointmentRepository(jdbc);

            // Clean up dependencies for tests
            seedDependencies();
        } catch (Exception e) {
            Assumptions.abort("PostgreSQL not available on " + host + ":" + port + ": " + e.getMessage());
        }
    }

    private void seedDependencies() {
        jdbc.execute("""
            insert into app_user (user_id, username, password_hash, user_status, last_login_at, user_create_at)
            values (1, 'system_appt_test', 'hash', true, current_timestamp, current_timestamp)
            on conflict (user_id) do nothing
        """);

        jdbc.execute("""
            insert into branch (branch_id, branch_code, branch_name, address, phone, branch_status, branch_create_by)
            values (99, '99', 'Appointment Test Branch', '123 Appt Rd', '020000099', true, 1)
            on conflict (branch_id) do update set branch_code = '99'
        """);

        jdbc.execute("""
            insert into staff (staff_id, staff_code, staff_fname, staff_lname, position, branch_id, staff_status, hire_date, staff_create_by)
            values (99, 'ST-APP-99', 'DrAppt', 'Smith', 'Therapist', 99, true, '2026-01-01', 1)
            on conflict (staff_id) do nothing
        """);

        jdbc.execute("""
            insert into service (service_id, service_code, service_name, service_type, service_price, service_status, service_create_by)
            values (99, 'SRV-APP-99', 'Appt Service 60', 'SingleVisit', 1200.00, true, 1)
            on conflict (service_id) do nothing
        """);

        jdbc.execute("""
            insert into patient (patient_id, hn, customer_type, title, patient_fname, patient_lname, gender, phone, branch_id, registered_by)
            values (99, '2699099999', 'Thai', 'นาย', 'PatientAppt', 'Test', 'Male', '0899999999', 99, 1)
            on conflict (patient_id) do nothing
        """);
    }

    @Test
    void appointmentLifecycleAndVisitFlowAgainstLivePostgres() {
        LocalDate testDate = LocalDate.of(2026, 10, 15);
        LocalTime testTime = LocalTime.of(14, 0);

        // Verify initial conflict check
        boolean initialConflict = repository.hasConflict(99L, testDate, testTime, null);
        assertThat(initialConflict).isFalse();

        // 1. Create Appointment
        DatabaseV2AppointmentRepository.AppointmentModel created = repository.create(
                99L, 99L, 99L, 99L, null, testDate, testTime, "Need knee therapy", 1L
        );
        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo("Confirmed");
        assertThat(created.note()).isEqualTo("Need knee therapy");

        // 2. Verify Conflict detected now
        boolean conflictNow = repository.hasConflict(99L, testDate, testTime, null);
        assertThat(conflictNow).isTrue();

        // But excluding its own id should not conflict
        boolean selfConflict = repository.hasConflict(99L, testDate, testTime, created.id());
        assertThat(selfConflict).isFalse();

        // 3. Check-In creates patient visit
        repository.updateStatus(created.id(), "Confirmed", "Arrived", "Patient arrived", 1L);
        Long visitId = repository.createPatientVisit(created.id(), 99L, 99L, 99L, 1L);
        assertThat(visitId).isNotNull();

        Optional<DatabaseV2AppointmentRepository.AppointmentModel> arrivedOpt = repository.findById(created.id());
        assertThat(arrivedOpt).isPresent();
        assertThat(arrivedOpt.get().status()).isEqualTo("Arrived");

        Optional<Long> visitFound = repository.findVisitIdByAppointmentId(created.id());
        assertThat(visitFound).contains(visitId);

        // 4. Start Service
        repository.updateStatus(created.id(), "Arrived", "InService", "Starting massage", 1L);
        repository.updateVisitStatus(created.id(), "InService", 1L);

        Optional<DatabaseV2AppointmentRepository.AppointmentModel> inServiceOpt = repository.findById(created.id());
        assertThat(inServiceOpt.get().status()).isEqualTo("InService");

        // 5. Complete Service
        repository.updateStatus(created.id(), "InService", "Completed", "Finished successfully", 1L);
        repository.updateVisitStatus(created.id(), "Completed", 1L);

        Optional<DatabaseV2AppointmentRepository.AppointmentModel> completedOpt = repository.findById(created.id());
        assertThat(completedOpt.get().status()).isEqualTo("Completed");

        // Verify patient_visit completed_at is set in database
        Boolean hasCompletedAt = jdbc.queryForObject(
                "select (completed_at is not null) from patient_visit where visit_id = ?",
                Boolean.class, visitId
        );
        assertThat(hasCompletedAt).isTrue();

        // 6. Verify history count
        Integer historyCount = jdbc.queryForObject(
                "select count(*) from appointment_status_history where appointment_id = ?",
                Integer.class, created.id()
        );
        // Initial booking + Arrived + InService + Completed = 4 history rows
        assertThat(historyCount).isGreaterThanOrEqualTo(4);
    }
}
