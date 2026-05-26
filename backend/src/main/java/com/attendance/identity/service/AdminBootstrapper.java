package com.attendance.identity.service;

import com.attendance.identity.domain.Role;
import com.attendance.identity.domain.User;
import com.attendance.identity.domain.UserStatus;
import com.attendance.identity.repository.RoleRepository;
import com.attendance.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
public class AdminBootstrapper implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapper.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String adminEmail;

    public AdminBootstrapper(UserRepository userRepository,
                             RoleRepository roleRepository,
                             PasswordEncoder passwordEncoder,
                             @Value("${attendance.bootstrap.admin-username:admin}") String adminUsername,
                             @Value("${attendance.bootstrap.admin-password:Admin@12345}") String adminPassword,
                             @Value("${attendance.bootstrap.admin-email:admin@local}") String adminEmail) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.adminEmail = adminEmail;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByUsernameIgnoreCase(adminUsername)) {
            return;
        }
        Role admin = roleRepository.findByName("ADMIN").orElse(null);
        if (admin == null) {
            log.warn("ADMIN role not seeded; skipping bootstrap admin user creation.");
            return;
        }

        User user = new User();
        user.setUsername(adminUsername);
        user.setEmail(adminEmail);
        user.setPasswordHash(passwordEncoder.encode(adminPassword));
        user.setStatus(UserStatus.ACTIVE);
        user.setDisplayName("System Administrator");
        user.setRoles(Set.of(admin));
        userRepository.save(user);

        log.warn("Bootstrap admin user created: username={}. ROTATE THIS PASSWORD BEFORE PRODUCTION USE.",
                adminUsername);
    }
}
