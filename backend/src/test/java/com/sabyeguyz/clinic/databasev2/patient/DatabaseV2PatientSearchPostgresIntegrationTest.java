package com.sabyeguyz.clinic.databasev2.patient;

import static org.assertj.core.api.Assertions.assertThat;

import com.sabyeguyz.clinic.databasev2.common.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DatabaseV2PatientSearchPostgresIntegrationTest {
    private JdbcTemplate jdbc;
    private DatabaseV2PatientRepository repository;

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
            repository = new DatabaseV2PatientRepository(jdbc);
            seedFixtures();
        } catch (Exception e) {
            PostgresIntegrationTestSupport.handleSetupFailure("PostgreSQL patient search setup failed on " + host + ":" + port, e);
        }
    }

    private void seedFixtures() {
        jdbc.execute("""
            insert into app_user (user_id, username, password_hash, user_status, last_login_at, user_create_at)
            values (1, 'system_patient_search_test', 'hash', true, current_timestamp, current_timestamp)
            on conflict (user_id) do nothing
        """);
        jdbc.execute("""
            insert into branch (branch_id, branch_code, branch_name, address, phone, branch_status, branch_create_by)
            values (199, '91', 'Search Fixture Branch', 'Search Fixture', '020000199', true, 1)
            on conflict (branch_id) do update set branch_code = '91'
        """);
        jdbc.update("delete from patient where patient_id between 19991 and 19993");
        jdbc.execute("""
            insert into patient (patient_id, hn, customer_type, title, patient_fname, patient_lname, gender, phone,
                                branch_id, national_id, passport_no, registered_by)
            values
              (19991, '2691099991', 'Thai', 'นาย', 'Twin', 'Name', 'Male', '0819901991', 199, '1999111111111', null, 1),
              (19992, '2691099992', 'Thai', 'นาย', 'Twin', 'Name', 'Male', '0819901992', 199, '1999222222222', null, 1),
              (19993, '2691099993', 'Foreigner', 'นาย', 'Passport', 'Marker', 'Male', '0819901993', 199, null, 'P-SEARCH-19993', 1)
        """);
    }

    @Test
    void filtersAllSupportedIdentifiersAndBranchWithMatchingTotalsAndStablePages() {
        assertSingle("2691099991", "2691099991", 19991L);
        assertSingle("0819901992", "2691099992", 19992L);
        assertSingle("1999222222222", "2691099992", 19992L);
        assertSingle("p-search-19993", "2691099993", 19993L);

        assertThat(repository.countSearch("Twin", "91")).isEqualTo(2);
        assertThat(repository.search("Twin", "91", 1, 0)).extracting(DatabaseV2PatientRepository.PatientRecord::id)
                .containsExactly(19991L);
        assertThat(repository.search("Twin", "91", 1, 1)).extracting(DatabaseV2PatientRepository.PatientRecord::id)
                .containsExactly(19992L);
        assertThat(repository.search("Twin", "91", 10, 0)).extracting(DatabaseV2PatientRepository.PatientRecord::id)
                .containsExactly(19991L, 19992L);
        assertThat(repository.countSearch("Twin", "01")).isZero();
        assertThat(repository.search("' OR 1=1 --", "91", 10, 0)).isEmpty();
        assertThat(repository.countSearch("' OR 1=1 --", "91")).isZero();
    }

    private void assertSingle(String query, String expectedHn, Long expectedId) {
        var rows = repository.search(query, "91", 10, 0);
        assertThat(repository.countSearch(query, "91")).isEqualTo(1);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).id()).isEqualTo(expectedId);
        assertThat(rows.get(0).hn()).isEqualTo(expectedHn);
    }
}
