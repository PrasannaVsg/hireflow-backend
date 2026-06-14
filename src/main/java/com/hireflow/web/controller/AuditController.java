package com.hireflow.web.controller;

import com.hireflow.domain.UserAuditLog;
import com.hireflow.repository.UserAuditLogRepository;
import com.hireflow.service.SecurityUtils;
import com.hireflow.web.dto.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final UserAuditLogRepository auditLogRepository;

    @GetMapping
    public PageResponse<UserAuditLog> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID userId,
            Pageable pageable) {

        UUID orgId = SecurityUtils.currentOrgId();
        Page<UserAuditLog> page;

        if (action != null) {
            page = auditLogRepository.findByOrganisationIdAndActionOrderByCreatedAtDesc(orgId, action, pageable);
        } else if (entityType != null) {
            page = auditLogRepository.findByOrganisationIdAndEntityTypeOrderByCreatedAtDesc(orgId, entityType, pageable);
        } else if (userId != null) {
            page = auditLogRepository.findByOrganisationIdAndUserIdOrderByCreatedAtDesc(orgId, userId, pageable);
        } else {
            page = auditLogRepository.findByOrganisationIdOrderByCreatedAtDesc(orgId, pageable);
        }

        return PageResponse.of(page);
    }
}
