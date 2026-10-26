package com.sabyeguyz.clinicv2.auth;

import com.sabyeguyz.clinicv2.role.Role;
import com.sabyeguyz.clinicv2.role.RoleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit first-install provisioning; never replaces accounts or passwords in an existing DB. */
@Component
public class InitialManagerProvisioner implements ApplicationRunner {
    private final AppUserRepository users;
    private final AppUserService service;
    private final RoleRepository roles;
    private final JdbcTemplate jdbc;
    private final String username;
    private final String email;
    private final String password;

    public InitialManagerProvisioner(AppUserRepository users, AppUserService service, RoleRepository roles, JdbcTemplate jdbc,
                                     @Value("${app.bootstrap.manager-username:}") String username,
                                     @Value("${app.bootstrap.manager-email:}") String email,
                                     @Value("${app.bootstrap.manager-password:}") String password) {
        this.users = users;
        this.service = service;
        this.roles = roles;
        this.jdbc = jdbc;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) return;
        // Serialize first-install provisioning if two instances start together.
        jdbc.execute("select pg_advisory_xact_lock(20261013)");
        if (jdbc.queryForObject("select count(*) from app_user where username <> 'system'", Long.class) != 0) return;
        if (username.isBlank() || username.length() > 50 || password.length() < 12)
            throw new IllegalArgumentException("Initial manager needs a username (1-50 characters) and a password of at least 12 characters");
        AppUser manager = service.register(username, email, password, null, null);
        Role role = roles.findByRoleName("Manager").orElseGet(() -> {
            Role created = new Role();
            created.setRoleName("Manager"); created.setRoleCreateAt(java.time.Instant.now());
            created.setRoleCreateBy(manager.getId());
            return roles.saveAndFlush(created);
        });
        service.assignRole(manager.getId(), role.getId());
    }
}
