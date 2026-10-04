package com.sabyeguyz.clinic.databasev2.treatment;

import com.sabyeguyz.clinic.databasev2.common.PostgresIntegrationTestSupport;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DatabaseV2TreatmentPostgresIntegrationTest {

    private JdbcTemplate jdbc;
    private DatabaseV2TreatmentRepository repository;

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
            repository = new DatabaseV2TreatmentRepository(jdbc);

            seedDependencies();
        } catch (Exception e) {
            PostgresIntegrationTestSupport.handleSetupFailure("PostgreSQL integration setup failed on " + host + ":" + port, e);
        }
    }

    private void seedDependencies() {
        jdbc.execute("""
            insert into app_user (user_id, username, password_hash, user_status, last_login_at, user_create_at)
            values (1, 'system_treatment_test', 'hash', true, current_timestamp, current_timestamp)
            on conflict (user_id) do nothing
        """);

        jdbc.execute("""
            insert into branch (branch_id, branch_code, branch_name, address, phone, branch_status, branch_create_by)
            values (97, '97', 'Treatment Test Branch', '123 Treatment St', '020000097', true, 1)
            on conflict (branch_id) do update set branch_code = '97'
        """);

        jdbc.execute("""
            insert into staff (staff_id, staff_code, staff_fname, staff_lname, position, branch_id, staff_status, hire_date, staff_create_by)
            values (97, 'ST-TRT-97', 'Doctor', 'Brown', 'Therapist', 97, true, '2026-01-01', 1)
            on conflict (staff_id) do nothing
        """);

        jdbc.execute("""
            insert into service (service_id, service_code, service_name, service_type, service_price, service_status, service_create_by)
            values (97, 'SRV-TRT-97', 'General Acupuncture', 'SingleVisit', 1500.00, true, 1)
            on conflict (service_id) do nothing
        """);

        jdbc.execute("""
            insert into patient (patient_id, hn, customer_type, title, patient_fname, patient_lname, gender, phone, branch_id, registered_by)
            values (97, '2697099997', 'Thai', 'นาย', 'TreatmentPatient', 'Test', 'Male', '0899999997', 97, 1)
            on conflict (patient_id) do nothing
        """);
    }

    @Test
    void activeServicesIncludeTreatmentService() {
        List<DatabaseV2TreatmentRepository.ServiceOptionRecord> services = repository.findActiveServices();
        assertThat(services).isNotEmpty();
        assertThat(services.stream().anyMatch(s -> "SRV-TRT-97".equals(s.code()))).isTrue();
    }

    @Test
    void createAndListTreatmentLifecycle() {
        // 1. Create walk-in treatment (no appointment)
        DatabaseV2TreatmentRepository.TreatmentRecord walkin = repository.createTreatment(
                97L, null, 97L, 97L, "Walk-in neck pain therapy", 1L
        );

        assertThat(walkin.id()).isNotNull();
        assertThat(walkin.patientId()).isEqualTo(97L);
        assertThat(walkin.appointmentId()).isNull();
        assertThat(walkin.staffId()).isEqualTo(97L);
        assertThat(walkin.serviceId()).isEqualTo(97L);
        assertThat(walkin.serviceName()).isEqualTo("General Acupuncture");
        assertThat(walkin.note()).isEqualTo("Walk-in neck pain therapy");

        // 2. List treatments for patient
        List<DatabaseV2TreatmentRepository.TreatmentRecord> treatments = repository.findByPatientId(97L);
        assertThat(treatments).isNotEmpty();
        assertThat(treatments.get(0).id()).isEqualTo(walkin.id());
    }
}
