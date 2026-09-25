package com.sabyeguyz.clinic.cypress;

import com.sabyeguyz.clinic.ClinicErpApplication;
import org.springframework.boot.SpringApplication;

/** Test-classpath entry point. The cypress profile keeps the real JWT security chain. */
public class CypressApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClinicErpApplication.class, args);
    }
}
