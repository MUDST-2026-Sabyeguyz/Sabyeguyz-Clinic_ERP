# Dependency scan baseline and remediation

Source: Actions run 43311257876, artifact clinic-actions-release-43311257876-1, trivy-backend.json. Fixed versions are verbatim scanner data, not exploitability conclusions.

Remediation selected: Boot BOM 4.1.2 moves the application to patched Spring Framework 7, Security 7, Data 4 and managed Jackson 2.21.7/Tomcat 11.0.27/Micrometer 1.17.2/PostgreSQL 42.7.14. Springdoc moved to 3.2.0. Flyway starter restores Boot 4 modular auto-configuration. Jackson 2 compatibility preserves existing APIs while request JsonNode models use Jackson 3. Frontend libxslt updated from Alpine repository. The Boot 4 major migration surfaced and was fixed/tested for request matcher, EntityScan, servlet context, Flyway, and Jackson compatibility.

| CVE | Package | Installed | Fixed versions in baseline | Dependency path and management |
|---|---|---|---|---|
| CVE-2026-68494 | com.fasterxml.jackson.core:jackson-core | 2.18.3 | 2.18.8, 2.21.4 | transitive; starter-web → starter-json → Jackson; Boot Jackson BOM |
| CVE-2026-89407 | com.fasterxml.jackson.core:jackson-core | 2.18.3 | 2.18.11, 2.21.7, 2.22.3 | transitive; starter-web → starter-json → Jackson; Boot Jackson BOM |
| CVE-2026-89425 | com.fasterxml.jackson.core:jackson-core | 2.18.3 | 2.21.7, 2.22.3, 2.18.11 | transitive; starter-web → starter-json → Jackson; Boot Jackson BOM |
| CVE-2026-54512 | com.fasterxml.jackson.core:jackson-databind | 2.18.3 | 2.18.8, 3.1.4, 2.21.4 | transitive; starter-web → starter-json → Jackson; Boot Jackson BOM |
| CVE-2026-54513 | com.fasterxml.jackson.core:jackson-databind | 2.18.3 | 2.18.8, 2.21.4, 3.1.4 | transitive; starter-web → starter-json → Jackson; Boot Jackson BOM |
| CVE-2026-68497 | com.fasterxml.jackson.core:jackson-databind | 2.18.3 | 2.18.10, 2.21.6, 2.22.2 | transitive; starter-web → starter-json → Jackson; Boot Jackson BOM |
| CVE-2026-91776 | com.fasterxml.jackson.core:jackson-databind | 2.18.3 | 2.18.11, 2.21.7, 2.22.3 | transitive; starter-web → starter-json → Jackson; Boot Jackson BOM |
| CVE-2026-91777 | com.fasterxml.jackson.core:jackson-databind | 2.18.3 | 2.21.7, 2.18.11, 2.22.3 | transitive; starter-web → starter-json → Jackson; Boot Jackson BOM |
| CVE-2026-40984 | io.micrometer:micrometer-core | 1.14.6 | 1.16.6, 1.15.12 | transitive; starter-actuator → micrometer-jakarta9 → core; Boot Micrometer BOM |
| CVE-2026-41293 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 9.0.118, 10.1.55, 11.0.22 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2026-43512 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 9.0.118, 10.1.55, 11.0.22 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2026-43515 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 9.0.118, 10.1.55, 11.0.22 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2026-55276 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 11.0.23, 10.1.56, 9.0.119 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2026-65182 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 11.0.25, 10.1.58, 9.0.121 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2026-65905 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 11.0.25, 10.1.58, 9.0.121 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2026-68525 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 11.0.25, 10.1.58, 9.0.121 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2025-48988 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 11.0.8, 10.1.42, 9.0.106 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2025-48989 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 11.0.10, 10.1.44, 9.0.108 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2025-52520 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 11.0.9, 10.1.43, 9.0.107 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2025-53506 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 9.0.107, 10.1.43, 11.0.9 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2025-55752 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 11.0.11, 10.1.45, 9.0.109 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2026-24734 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 11.0.18, 10.1.52, 9.0.115 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2026-24880 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 9.0.116, 10.1.52, 11.0.20 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2026-34483 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 9.0.116, 10.1.54, 11.0.21 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2026-34487 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 9.0.117, 10.1.54, 11.0.21 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2026-41284 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 9.0.118, 10.1.55, 11.0.22 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2026-42498 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 9.0.118, 10.1.55, 11.0.22 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2026-43513 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 9.0.118, 10.1.55, 11.0.22 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2026-53404 | org.apache.tomcat.embed:tomcat-embed-core | 10.1.40 | 11.0.23, 10.1.56, 9.0.119 | transitive; starter-web → starter-tomcat → core; Boot Tomcat BOM |
| CVE-2025-49146 | org.postgresql:postgresql | 42.7.5 | 42.7.7 | direct; postgresql direct runtime; Boot PostgreSQL property |
| CVE-2026-42198 | org.postgresql:postgresql | 42.7.5 | 42.7.11 | direct; postgresql direct runtime; Boot PostgreSQL property |
| CVE-2026-54291 | org.postgresql:postgresql | 42.7.5 | 42.7.12 | direct; postgresql direct runtime; Boot PostgreSQL property |
| CVE-2026-40973 | org.springframework.boot:spring-boot | 3.4.5 | 4.0.6, 3.5.14 | transitive; starter-web → starter → spring-core; Boot Framework BOM |
| CVE-2026-41695 | org.springframework.data:spring-data-commons | 3.4.5 | 4.0.6, 3.5.12 | transitive; starter-data-jpa → spring-data-jpa → commons; Boot Spring Data BOM |
| CVE-2026-41716 | org.springframework.data:spring-data-commons | 3.4.5 | 4.0.6, 3.5.12 | transitive; starter-data-jpa → spring-data-jpa → commons; Boot Spring Data BOM |
| CVE-2025-41232 | org.springframework.security:spring-security-core | 6.4.5 | 6.4.6 | transitive; starter-oauth2-resource-server → Security; Boot Security BOM |
| CVE-2025-41248 | org.springframework.security:spring-security-core | 6.4.5 | 6.4.10, 6.5.4 | transitive; starter-oauth2-resource-server → Security; Boot Security BOM |
| CVE-2026-22732 | org.springframework.security:spring-security-web | 6.4.5 | 6.5.9, 7.0.4 | transitive; starter-oauth2-resource-server → Security; Boot Security BOM |
| CVE-2025-41249 | org.springframework:spring-core | 6.2.6 | 6.2.11 | transitive; starter-web → starter → spring-core; Boot Framework BOM |
| CVE-2026-41850 | org.springframework:spring-expression | 6.2.6 | 7.0.8, 6.2.19 | transitive; starter-web → spring-webmvc → spring-expression; Boot Framework BOM |
| CVE-2026-47884 | org.springframework:spring-webmvc | 6.2.6 | 7.0.9 | transitive; starter-web → spring-webmvc; Boot Framework BOM |
| CVE-2026-47890 | org.springframework:spring-webmvc | 6.2.6 | 7.0.9 | transitive; starter-web → spring-webmvc; Boot Framework BOM |
| CVE-2026-41842 | org.springframework:spring-webmvc | 6.2.6 | 7.0.8, 6.2.19 | transitive; starter-web → spring-webmvc; Boot Framework BOM |
| CVE-2026-41845 | org.springframework:spring-webmvc | 6.2.6 | 7.0.8, 6.2.19 | transitive; starter-web → spring-webmvc; Boot Framework BOM |
