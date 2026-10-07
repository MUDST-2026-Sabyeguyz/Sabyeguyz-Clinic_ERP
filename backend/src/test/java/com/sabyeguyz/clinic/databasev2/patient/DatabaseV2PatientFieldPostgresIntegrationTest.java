package com.sabyeguyz.clinic.databasev2.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2Branch;
import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2BranchRepository;
import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import com.sabyeguyz.clinic.databasev2.common.PostgresIntegrationTestSupport;
import com.sabyeguyz.clinic.databasev2.masterdata.DatabaseV2MasterDataRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;

/** API/controller to PostgreSQL readback for the confirmed patient field contract. */
class DatabaseV2PatientFieldPostgresIntegrationTest {
    private JdbcTemplate jdbc;
    private DatabaseV2PatientRepository repository;
    private DatabaseV2PatientController controller;
    private TransactionTemplate transactions;
    private final List<Long> createdPatientIds = Collections.synchronizedList(new ArrayList<>());

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
            transactions = new TransactionTemplate(new DataSourceTransactionManager(ds));
            jdbc.execute("select 1");
            repository = new DatabaseV2PatientRepository(jdbc);
            jdbc.execute("""
                insert into app_user (user_id, username, password_hash, user_status, last_login_at, user_create_at)
                values (1, 'system_patient_field_test', 'hash', true, current_timestamp, current_timestamp)
                on conflict (user_id) do nothing
            """);
            jdbc.execute("""
                insert into branch (branch_id, branch_code, branch_name, address, phone, branch_status, branch_create_by)
                values (199, '91', 'Field Fixture Branch', 'Field Fixture', '020000199', true, 1)
                on conflict (branch_id) do update set branch_code = '91'
            """);
            DatabaseV2BranchRepository branches = mock(DatabaseV2BranchRepository.class);
            when(branches.findByCode("91")).thenReturn(Optional.of(new DatabaseV2Branch(199L, "91", "Field Fixture Branch", "", "", true)));
            DatabaseV2MasterDataRepository masterData = mock(DatabaseV2MasterDataRepository.class);
            when(masterData.findByCategory(any())).thenReturn(List.of());
            CurrentActorProvider actor = mock(CurrentActorProvider.class);
            when(actor.getActorIdOrDefault(any(), any())).thenReturn(1L);
            controller = new DatabaseV2PatientController(repository, branches, masterData, actor,
                    Clock.fixed(Instant.parse("2026-10-06T05:00:00Z"), ZoneId.of("Asia/Bangkok")), org.mockito.Mockito.mock(com.sabyeguyz.clinic.databasev2.security.BranchAccessGuard.class));
        } catch (Exception e) {
            PostgresIntegrationTestSupport.handleSetupFailure("PostgreSQL patient field setup failed on " + host + ":" + port, e);
        }
    }

    @AfterEach
    void cleanupOwnedRows() {
        if (jdbc != null) createdPatientIds.forEach(id -> jdbc.update("delete from patient where patient_id = ?", id));
    }

    @Test
    void postPutGetAndSqlReadBackKeepConfirmedFieldsAndNullableValues() {
        String uniquePhone = "091" + Long.toString(System.nanoTime()).substring(0, 7);
        var create = request("Ada", "Lovelace Byron", uniquePhone, null, null, "  ", "  ", "NATIONAL_ID", "");
        var created = controller.register(create, mock(Authentication.class));
        createdPatientIds.add(created.id());
        var postRead = controller.getProfile(created.id());
        var postSql = jdbc.queryForMap("select patient_id, hn, branch_id, patient_fname, patient_lname, patient_nickname, national_id, passport_no, birthdate, address, customer_type from patient where patient_id = ?", created.id());

        assertThat(postRead.id()).isEqualTo(created.id());
        assertThat(postRead.hn()).isEqualTo(created.hn());
        assertThat(postRead.firstName()).isEqualTo("Ada");
        assertThat(postRead.lastName()).isEqualTo("Lovelace Byron");
        assertThat(postRead.birthDate()).isNull();
        assertThat(postRead.address()).isNull();
        assertThat(postSql).containsEntry("patient_id", created.id()).containsEntry("hn", created.hn())
                .containsEntry("branch_id", 199L).containsEntry("patient_fname", "Ada")
                .containsEntry("patient_lname", "Lovelace Byron").containsEntry("national_id", null)
                .containsEntry("passport_no", null).containsEntry("birthdate", null)
                .containsEntry("address", null).containsEntry("customer_type", "Thai");

        var update = request("Grace", "Hopper Lovelace", uniquePhone, LocalDate.of(1930, 12, 9),
                "  Harbor View  ", "  Bea  ", "  ", "NATIONAL_ID", "ไทย");
        controller.update(created.id(), update, mock(Authentication.class));
        var putRead = controller.getProfile(created.id());
        var putSql = jdbc.queryForMap("select patient_id, hn, branch_id, patient_fname, patient_lname, patient_nickname, passport_no, birthdate, address from patient where patient_id = ?", created.id());

        assertThat(putRead.id()).isEqualTo(created.id());
        assertThat(putRead.hn()).isEqualTo(created.hn());
        assertThat(putRead.branch().code()).isEqualTo("91");
        assertThat(putRead.firstName()).isEqualTo("Grace");
        assertThat(putRead.lastName()).isEqualTo("Hopper Lovelace");
        assertThat(putRead.identityDocumentType()).isEqualTo("");
        assertThat(putRead.identityNumber()).isEqualTo("");
        assertThat(putRead.nickname()).isEqualTo("Bea");
        assertThat(putSql).containsEntry("patient_id", created.id()).containsEntry("hn", created.hn())
                .containsEntry("branch_id", 199L).containsEntry("patient_fname", "Grace")
                .containsEntry("patient_lname", "Hopper Lovelace").containsEntry("patient_nickname", "Bea")
                .containsEntry("passport_no", null)
                .containsEntry("address", "Harbor View");
        assertThat(((Date) putSql.get("birthdate")).toLocalDate()).isEqualTo(LocalDate.of(1930, 12, 9));
    }

    @Test
    void acceptsOptionalForeignPassportAndKeepsItsAlphanumericValue() {
        String uniquePhone = "092" + Long.toString(System.nanoTime()).substring(0, 7);
        var create = request("Mina", "van der Meer", uniquePhone, LocalDate.of(1994, 3, 4),
                "", "", " P-Alpha 9 ", "PASSPORT", "Canada");
        var created = controller.register(create, mock(Authentication.class));
        createdPatientIds.add(created.id());
        var profile = controller.getProfile(created.id());
        var row = jdbc.queryForMap("select passport_no, national_id, patient_fname, patient_lname from patient where patient_id = ?", created.id());

        assertThat(profile.identityDocumentType()).isEqualTo("PASSPORT");
        assertThat(profile.identityNumber()).isEqualTo("P-Alpha 9");
        assertThat(profile.address()).isNull();
        assertThat(row).containsEntry("passport_no", "P-Alpha 9").containsEntry("national_id", null)
                .containsEntry("patient_fname", "Mina").containsEntry("patient_lname", "van der Meer");
    }

    @Test
    void actualRegistrationUsesBangkokMonthAndYearAtBoundaryInstants() {
        assertBoundaryRegistration("2026-10-31T16:59:59Z", "269110");
        assertBoundaryRegistration("2026-10-31T17:00:00Z", "269111");
        assertBoundaryRegistration("2026-12-31T16:59:59Z", "269112");
        assertBoundaryRegistration("2026-12-31T17:00:00Z", "279101");
        assertThat(jdbc.queryForObject("select count(*) from hn_sequence where branch_id = 199 and year_month in ('2610', '2611', '2612', '2701')", Integer.class))
                .isEqualTo(4);
    }

    @Test
    void concurrentActualControllerRegistrationsAllocateUniqueHnsAndPersistAllRows() throws Exception {
        int count = 8;
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            List<Future<DatabaseV2PatientController.PatientResponse>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int suffix = i;
                futures.add(executor.submit(() -> transactions.execute(status -> {
                    String phone = "095" + String.format("%07d", suffix);
                    var response = controller.register(request("Concurrent", "Field " + suffix, phone, null, null,
                            "", "", "NATIONAL_ID", "ไทย"), mock(Authentication.class));
                    createdPatientIds.add(response.id());
                    return response;
                })));
            }
            List<DatabaseV2PatientController.PatientResponse> responses = new ArrayList<>();
            for (Future<DatabaseV2PatientController.PatientResponse> future : futures) {
                var response = future.get(30, TimeUnit.SECONDS);
                responses.add(response);
            }
            assertThat(responses).hasSize(count);
            assertThat(responses.stream().map(DatabaseV2PatientController.PatientResponse::hn).distinct()).hasSize(count);
            assertThat(responses).allSatisfy(response -> assertThat(repository.findById(response.id())).isPresent());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void failedInsertAfterHnAllocationRollsBackCounterAndPatientRow() {
        String phone = "096" + Long.toString(System.nanoTime()).substring(0, 7);
        List<Integer> counterBefore = jdbc.queryForList(
                "select last_running_no from hn_sequence where branch_id = 199 and year_month = '2610'", Integer.class);
        String tooLongAddress = "x".repeat(256);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transactions.execute(status -> controller.register(
                request("Rollback", "Fixture", phone, null, tooLongAddress, "", "", "NATIONAL_ID", "ไทย"),
                mock(Authentication.class))))
                .isInstanceOf(DataAccessException.class);

        List<Integer> counterAfter = jdbc.queryForList(
                "select last_running_no from hn_sequence where branch_id = 199 and year_month = '2610'", Integer.class);
        Integer patientRows = jdbc.queryForObject("select count(*) from patient where phone = ?", Integer.class, phone);
        assertThat(counterAfter).isEqualTo(counterBefore);
        assertThat(patientRows).isZero();
    }

    @Test
    void exhaustedActualRegistrationDoesNotCreatePatientOrAdvanceCounter() {
        jdbc.update("""
                insert into hn_sequence (branch_id, year_month, last_running_no, last_running_at, created_at)
                values (199, '5110', 9999, current_timestamp, current_timestamp)
                on conflict (branch_id, year_month) do update set last_running_no = 9999
                """);
        DatabaseV2BranchRepository branches = mock(DatabaseV2BranchRepository.class);
        when(branches.findByCode("91")).thenReturn(Optional.of(new DatabaseV2Branch(199L, "91", "Field Fixture Branch", "", "", true)));
        DatabaseV2MasterDataRepository masterData = mock(DatabaseV2MasterDataRepository.class);
        when(masterData.findByCategory(any())).thenReturn(List.of());
        CurrentActorProvider actor = mock(CurrentActorProvider.class);
        when(actor.getActorIdOrDefault(any(), any())).thenReturn(1L);
        var clockedController = new DatabaseV2PatientController(repository, branches, masterData, actor,
                Clock.fixed(Instant.parse("2051-10-06T05:00:00Z"), ZoneId.of("Asia/Bangkok")), org.mockito.Mockito.mock(com.sabyeguyz.clinic.databasev2.security.BranchAccessGuard.class));
        String phone = "097" + Long.toString(System.nanoTime()).substring(0, 7);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transactions.execute(status -> clockedController.register(
                request("Exhausted", "Fixture", phone, null, null, "", "", "NATIONAL_ID", "ไทย"),
                mock(Authentication.class))))
                .isInstanceOf(HnCapacityExceededException.class);

        Integer lastRunning = jdbc.queryForObject(
                "select last_running_no from hn_sequence where branch_id = 199 and year_month = '5110'", Integer.class);
        Integer patientRows = jdbc.queryForObject("select count(*) from patient where phone = ?", Integer.class, phone);
        assertThat(lastRunning).isEqualTo(9999);
        assertThat(patientRows).isZero();
    }

    private void assertBoundaryRegistration(String instant, String expectedPrefix) {
        DatabaseV2BranchRepository branches = mock(DatabaseV2BranchRepository.class);
        when(branches.findByCode("91")).thenReturn(Optional.of(new DatabaseV2Branch(199L, "91", "Field Fixture Branch", "", "", true)));
        DatabaseV2MasterDataRepository masterData = mock(DatabaseV2MasterDataRepository.class);
        when(masterData.findByCategory(any())).thenReturn(List.of());
        CurrentActorProvider actor = mock(CurrentActorProvider.class);
        when(actor.getActorIdOrDefault(any(), any())).thenReturn(1L);
        var clockedController = new DatabaseV2PatientController(repository, branches, masterData, actor,
                Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Bangkok")), org.mockito.Mockito.mock(com.sabyeguyz.clinic.databasev2.security.BranchAccessGuard.class));
        String uniquePhone = "093" + Long.toString(System.nanoTime()).substring(0, 7);
        var response = clockedController.register(request("Boundary", "Fixture", uniquePhone, null, null,
                "", "", "NATIONAL_ID", "ไทย"), mock(Authentication.class));
        createdPatientIds.add(response.id());
        assertThat(response.hn()).startsWith(expectedPrefix).hasSize(10);
    }

    private static DatabaseV2PatientController.RegisterRequest request(
            String firstName, String lastName, String phone, LocalDate birthDate, String address,
            String nickname, String identityNumber, String documentType, String nationality) {
        boolean foreigner = "PASSPORT".equalsIgnoreCase(documentType) || "ต่างชาติ".equals(nationality);
        return new DatabaseV2PatientController.RegisterRequest(
                "91", null, null, null, "นางสาว", null, firstName, lastName, birthDate, "หญิง", phone,
                documentType, identityNumber, address, null, null, null, null, null, null, null,
                nickname, null, nationality.isBlank() ? "ไทย" : nationality, null, null, null,
                foreigner ? "Foreigner" : "Thai");
    }
}
