package com.sabyeguyz.clinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.sabyeguyz.clinic.config.SecurityConfig;
import com.sabyeguyz.clinic.config.WebConfig;

@SpringBootApplication(scanBasePackages = "com.sabyeguyz.clinicv2")
@EntityScan("com.sabyeguyz.clinicv2")
@EnableJpaRepositories("com.sabyeguyz.clinicv2")
@Import({SecurityConfig.class, WebConfig.class})
public class ClinicErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicErpApplication.class, args);
    }
}
