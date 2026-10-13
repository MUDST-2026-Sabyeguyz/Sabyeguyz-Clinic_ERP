package com.sabyeguyz.clinicv2;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the application on the v2 schema (db/migration_new). It scans only
 * com.sabyeguyz.clinicv2, so none of the old com.sabyeguyz.clinic controllers, entities
 * or tables are involved. The v2 profile (application-v2.properties) is active unless
 * another profile is given.
 *
 * <p>Run it with {@code mvn -Pv2 spring-boot:run}, or from the packaged jar with
 * {@code java -Dloader.main=com.sabyeguyz.clinicv2.ClinicV2Application -cp app.jar
 * org.springframework.boot.loader.launch.PropertiesLauncher}.</p>
 */
@SpringBootApplication
public class ClinicV2Application {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ClinicV2Application.class);
        application.setDefaultProperties(Map.of("spring.profiles.default", "v2"));
        application.run(args);
    }
}
