package com.hireflow.service;

import com.hireflow.domain.UserAuditLog;
import com.hireflow.repository.UserAuditLogRepository;
import com.hireflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuditService {

    private final UserAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * Captures userId/orgId on the calling (request) thread before handing off to async.
     */
    public void log(String action, String entityType, UUID entityId, String entityName, String details) {
        UUID userId = SecurityUtils.currentUserId();
        UUID orgId  = SecurityUtils.currentOrgId();
        writeAsync(userId, orgId, action, entityType, entityId, entityName, details);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeAsync(UUID userId, UUID orgId, String action, String entityType,
                           UUID entityId, String entityName, String details) {
        try {
            String userName = resolveUserName(userId);

            UserAuditLog entry = UserAuditLog.builder()
                    .organisationId(orgId)
                    .userId(userId)
                    .userName(userName)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .entityName(entityName)
                    .details(details)
                    .createdAt(Instant.now())
                    .build();

            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Failed to write audit log for action={}: {}", action, e.getMessage());
        }
    }

    private String resolveUserName(UUID userId) {
        try {
            return userRepository.findById(userId)
                    .map(u -> u.getFullName())
                    .orElse("Unknown");
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
