package com.sabyeguyz.schemacheck;

import com.sabyeguyz.clinicv2.ClinicV2Application;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Replays an existing V1001 database created before the email V4 migration was added. */
public final class FlywayOutOfOrderUpgradeCheck {
    public static void main(String[] args) throws Exception {
        String url = required("DB_URL");
        String user = required("DB_USER");
        String password = required("DB_PASSWORD");
        Path previousMigrations = Files.createTempDirectory("clinic-before-email-v4-");
        try {
            for (String name : List.of("V1__initial_schema.sql", "V2__integration_runtime_compatibility.sql",
                    "V3__reference_data.sql", "V1001__historical_assessment_transaction_type.sql")) {
                try (var source = FlywayOutOfOrderUpgradeCheck.class.getResourceAsStream("/db/migration/" + name)) {
                    if (source == null) throw new IllegalStateException("Missing migration " + name);
                    Files.copy(source, previousMigrations.resolve(name));
                }
            }
            Flyway.configure().dataSource(url, user, password)
                    .locations("filesystem:" + previousMigrations.toAbsolutePath()).load().migrate();

            SpringApplication app = new SpringApplication(ClinicV2Application.class);
            try (var context = app.run("--spring.profiles.active=v2", "--server.port=0",
                    "--spring.datasource.url=" + url, "--spring.datasource.username=" + user,
                    "--spring.datasource.password=" + password)) {
                var db = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
                Integer migrated = db.queryForObject("select count(*) from flyway_schema_history where success", Integer.class);
                if (migrated == null || migrated != 7) throw new AssertionError("Expected 7 migrations, got " + migrated);
                Integer version4Rank = db.queryForObject(
                        "select installed_rank from flyway_schema_history where version='4'", Integer.class);
                Integer version1001Rank = db.queryForObject(
                        "select installed_rank from flyway_schema_history where version='1001'", Integer.class);
                if (version4Rank == null || version1001Rank == null || version4Rank <= version1001Rank) {
                    throw new AssertionError("Email V4 was not applied after historical V1001");
                }
                Integer emailColumns = db.queryForObject("select count(*) from information_schema.columns "
                        + "where table_name='app_user' and column_name='email'", Integer.class);
                if (emailColumns == null || emailColumns != 1) throw new AssertionError("Email V4 did not apply");
                System.out.println("FLYWAY_OUT_OF_ORDER_UPGRADE_OK");
            }
        } finally {
            try (var paths = Files.list(previousMigrations)) {
                for (Path path : paths.toList()) Files.deleteIfExists(path);
            }
            Files.deleteIfExists(previousMigrations);
        }
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set " + key);
        return value;
    }
}
