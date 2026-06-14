package com.hireflow.repository;

import com.hireflow.domain.UserAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserAuditLogRepository extends JpaRepository<UserAuditLog, UUID> {

    Page<UserAuditLog> findByOrganisationIdOrderByCreatedAtDesc(UUID organisationId, Pageable pageable);

    Page<UserAuditLog> findByOrganisationIdAndActionOrderByCreatedAtDesc(UUID organisationId, String action, Pageable pageable);

    Page<UserAuditLog> findByOrganisationIdAndEntityTypeOrderByCreatedAtDesc(UUID organisationId, String entityType, Pageable pageable);

    Page<UserAuditLog> findByOrganisationIdAndUserIdOrderByCreatedAtDesc(UUID organisationId, UUID userId, Pageable pageable);
}
