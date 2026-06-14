package com.hireflow.config;

import com.hireflow.domain.User;
import com.hireflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String ADMIN_EMAIL = "admin@hireflow.ai";
    private static final String ADMIN_PASSWORD = "ChangeMe!2026";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userRepository.findByEmailWithOrg(ADMIN_EMAIL).ifPresent(user -> {
            String hash = user.getPasswordHash();
            // Fix invalid/placeholder hash from V4 seed migration
            if (hash == null || (!hash.startsWith("$2a$") && !hash.startsWith("$2b$"))) {
                String newHash = passwordEncoder.encode(ADMIN_PASSWORD);
                user.setPasswordHash(newHash);
                userRepository.save(user);
                log.info("DataInitializer: fixed BCrypt hash for {}", ADMIN_EMAIL);
            }
        });
    }
}
