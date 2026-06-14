package com.hireflow.config;

import com.hireflow.service.SecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> {
            try {
                return Optional.of(SecurityUtils.currentUserId());
            } catch (Exception e) {
                return Optional.empty();
            }
        };
    }
}
