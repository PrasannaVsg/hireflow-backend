package com.hireflow.repository;

import com.hireflow.domain.AiAuditLog;
import com.hireflow.domain.enums.AiOperation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, UUID> {

    Page<AiAuditLog> findByOrganisationId(UUID organisationId, Pageable pageable);

    @Query("select count(a) from AiAuditLog a " +
           "where a.organisationId = :orgId and a.operation = :op " +
           "and a.createdAt >= :since")
    long countByOrgAndOperationSince(@Param("orgId") UUID orgId,
                                     @Param("op") AiOperation op,
                                     @Param("since") Instant since);

    @Query("select coalesce(sum(a.requestTokens + a.responseTokens), 0) " +
           "from AiAuditLog a where a.organisationId = :orgId and a.createdAt >= :since")
    long sumTokensSince(@Param("orgId") UUID orgId, @Param("since") Instant since);

    @Query("select coalesce(sum(a.requestTokens + a.responseTokens), 0) " +
           "from AiAuditLog a where a.organisationId = :orgId and a.operation = :op and a.createdAt >= :since")
    long sumTokensByOperationSince(@Param("orgId") UUID orgId,
                                   @Param("op") AiOperation op,
                                   @Param("since") Instant since);

    @Query("select a from AiAuditLog a where a.organisationId = :orgId " +
           "order by a.createdAt desc")
    List<AiAuditLog> findRecentByOrg(@Param("orgId") UUID orgId, Pageable pageable);
}
