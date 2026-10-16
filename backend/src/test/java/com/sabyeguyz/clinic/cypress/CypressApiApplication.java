package com.sabyeguyz.clinic.cypress;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import com.sabyeguyz.clinic.ClinicErpApplication;

/** Test-classpath entry point. The cypress profile keeps the real JWT security chain. */
@SpringBootApplication(scanBasePackages = "com.sabyeguyz.clinic")
@ComponentScan(basePackages = "com.sabyeguyz.clinic", excludeFilters =
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ClinicErpApplication.class))
@EntityScan("com.sabyeguyz.clinic")
@EnableJpaRepositories("com.sabyeguyz.clinic")
public class CypressApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(CypressApiApplication.class, args);
    }
}
