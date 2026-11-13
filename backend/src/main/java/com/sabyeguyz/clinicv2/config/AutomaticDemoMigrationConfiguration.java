package com.sabyeguyz.clinicv2.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Only local/demo deployments opt into automatic demo selection; normal application defaults stay schema-only. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "clinic.seed-mode")
public class AutomaticDemoMigrationConfiguration {
    @Bean
    FlywayMigrationStrategy automaticDemoMigrationStrategy(
            @Value("${clinic.seed-mode:none}") String mode,
            @Value("${clinic.database-kind:unspecified}") String databaseKind,
            Environment environment) {
        return flyway -> DatabaseMigrations.migrate(flyway.getConfiguration(), mode,
                databaseKind, String.join(",", environment.getActiveProfiles()));
    }
}
