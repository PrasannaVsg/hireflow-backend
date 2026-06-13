package com.hireflow.service;

import com.hireflow.domain.AiAuditLog;
import com.hireflow.domain.enums.AiOperation;
import com.hireflow.repository.AiAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AiAuditLogRepository auditRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID orgId, UUID actorId, AiOperation op, String model,
                              Integer reqTokens, Integer respTokens, long latencyMs,
                              UUID targetId, String prompt) {
        auditRepository.save(AiAuditLog.builder()
                .organisationId(orgId)
                .actorUserId(actorId)
                .operation(op)
                .model(model)
                .requestTokens(reqTokens)
                .responseTokens(respTokens)
                .latencyMs(latencyMs)
                .success(true)
                .promptHash(sha256(prompt))
                .targetEntityId(targetId)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID orgId, UUID actorId, AiOperation op, String model,
                              long latencyMs, UUID targetId, String error) {
        auditRepository.save(AiAuditLog.builder()
                .organisationId(orgId)
                .actorUserId(actorId)
                .operation(op)
                .model(model)
                .latencyMs(latencyMs)
                .success(false)
                .errorMessage(truncate(error))
                .targetEntityId(targetId)
                .build());
    }

    private String sha256(String input) {
        if (input == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
