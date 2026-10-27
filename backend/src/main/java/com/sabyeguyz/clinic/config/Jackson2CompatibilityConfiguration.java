package com.sabyeguyz.clinic.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides the Jackson 2 mapper still used by calendar, audit, and legacy integrations. */
@Configuration(proxyBeanMethods = false)
public class Jackson2CompatibilityConfiguration {
    @Bean
    ObjectMapper jackson2ObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
