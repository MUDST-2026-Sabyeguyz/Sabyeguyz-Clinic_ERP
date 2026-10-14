package com.sabyeguyz.schemacheck;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.dialect.Dialect;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Table;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Checks the clinicv2 entities against a database built from production Flyway migrations.
 *
 * <p>Step 1 lists every column whose database type Hibernate would reject (so one run shows all
 * problems, not just the first). Step 2 boots the whole clinicv2 bean graph over that database with
 * hibernate.hbm2ddl.auto=validate, which is the authoritative schema validation and also proves that
 * every repository query and every service and controller wires. Connection settings come from the
 * DB_URL, DB_USER and DB_PASSWORD environment variables. Lives outside com.sabyeguyz.clinic so the
 * Spring Boot app and its Cypress profile never component-scan it.</p>
 */
public class SchemaCheck {
    private static final String ENTITY_PACKAGE = "com.sabyeguyz.clinicv2";

    public static void main(String[] args) throws Exception {
        boolean ok = reportColumnTypes();
        ok &= validateAndWire();
        System.out.println(ok ? "SCHEMA_CHECK_OK" : "SCHEMA_CHECK_FAILED");
        System.exit(ok ? 0 : 1);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new IllegalStateException("Set the " + name + " environment variable");
        }
        return value;
    }

    private static boolean reportColumnTypes() throws Exception {
        String url = env("DB_URL");
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", url)
                .applySetting("hibernate.connection.username", env("DB_USER"))
                .applySetting("hibernate.connection.password", env("DB_PASSWORD"))
                .build();
        MetadataSources sources = new MetadataSources(registry);
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (BeanDefinition definition : scanner.findCandidateComponents(ENTITY_PACKAGE)) {
            sources.addAnnotatedClass(Class.forName(definition.getBeanClassName()));
        }
        MetadataImplementor metadata = (MetadataImplementor) sources.buildMetadata();
        Dialect dialect = metadata.getDatabase().getDialect();

        List<String> problems = new ArrayList<>();
        int columns = 0;
        try (Connection connection = DriverManager.getConnection(url, env("DB_USER"), env("DB_PASSWORD"))) {
            for (Table table : metadata.collectTableMappings()) {
                if (!table.isPhysicalTable()) continue;
                Map<String, int[]> codes = new HashMap<>();
                Map<String, String> names = new HashMap<>();
                var requiredColumns = new java.util.HashSet<String>();
                for (String candidate : new String[] {table.getName(), table.getName().toUpperCase(Locale.ROOT)}) {
                    try (ResultSet rs = connection.getMetaData().getColumns(null, null, candidate, null)) {
                        while (rs.next()) {
                            String column = rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
                            codes.put(column, new int[] {rs.getInt("DATA_TYPE")});
                            names.put(column, rs.getString("TYPE_NAME"));
                            if (rs.getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNoNulls
                                    && rs.getString("COLUMN_DEF") == null
                                    && !"YES".equals(rs.getString("IS_AUTOINCREMENT"))) requiredColumns.add(column);
                        }
                    }
                }
                if (codes.isEmpty()) {
                    problems.add("MISSING TABLE  " + table.getName());
                    continue;
                }
                for (Column column : table.getColumns()) {
                    columns++;
                    String name = column.getName().toLowerCase(Locale.ROOT);
                    requiredColumns.remove(name);
                    if (!codes.containsKey(name)) {
                        problems.add("MISSING COLUMN " + table.getName() + "." + name);
                        continue;
                    }
                    int expectedCode = column.getSqlTypeCode(metadata);
                    String expectedType = column.getSqlType(metadata);
                    int actualCode = codes.get(name)[0];
                    String actualType = names.get(name);
                    boolean match = dialect.equivalentTypes(expectedCode, actualCode)
                            || normalize(expectedType).startsWith(normalize(actualType));
                    if (!match) {
                        problems.add("TYPE MISMATCH  " + table.getName() + "." + name + "  expected=" + expectedType
                                + " (code " + expectedCode + ")  actual=" + actualType + " (code " + actualCode + ")");
                    }
                }
                requiredColumns.forEach(name -> problems.add("UNMAPPED REQUIRED COLUMN " + table.getName() + "." + name));
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
        System.out.println("Checked " + columns + " mapped columns, " + problems.size() + " problem(s)");
        problems.forEach(p -> System.out.println("  " + p));
        return problems.isEmpty();
    }

    private static String normalize(String type) {
        return type == null ? "" : type.toLowerCase(Locale.ROOT).trim();
    }

    private static boolean validateAndWire() {
        try (var context = new AnnotationConfigApplicationContext(WiringConfig.class)) {
            int controllers = context.getBeanNamesForAnnotation(org.springframework.web.bind.annotation.RestController.class).length;
            int services = context.getBeanNamesForAnnotation(org.springframework.stereotype.Service.class).length;
            int entities = context.getBean(EntityManagerFactory.class).getMetamodel().getEntities().size();
            System.out.println("Hibernate validate passed: " + entities + " entities, " + controllers
                    + " controllers and " + services + " services wired over PostgreSQL");
            return true;
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            System.out.println("Hibernate validate / wiring FAILED: " + root);
            e.printStackTrace(System.out);
            return false;
        }
    }

    @Configuration
    @EnableJpaRepositories(basePackages = ENTITY_PACKAGE)
    @ComponentScan(basePackages = ENTITY_PACKAGE, excludeFilters = {
            @ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.ANNOTATION, classes = Configuration.class),
            @ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.REGEX, pattern = "com\\.sabyeguyz\\.clinicv2\\.cypress\\..*")
    })
    static class WiringConfig {
        private static final byte[] SECRET = "schema-check-secret-schema-check-secret".getBytes(StandardCharsets.UTF_8);

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(env("DB_URL"), env("DB_USER"), env("DB_PASSWORD"));
        }

        @Bean
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(ENTITY_PACKAGE);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        JwtEncoder jwtEncoder() {
            return new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(SECRET, "HmacSHA256")));
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(SECRET, "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256).build();
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
