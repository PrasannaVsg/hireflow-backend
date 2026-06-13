package com.hireflow.service;

import com.hireflow.domain.enums.AiOperation;
import com.hireflow.repository.AiAuditLogRepository;
import com.hireflow.repository.CandidateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final AiAuditLogRepository auditLogRepository;

    public Map<String, Object> overview(int days) {
        UUID orgId = SecurityUtils.currentOrgId();
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        long totalTokens = auditLogRepository.sumTokensSince(orgId, since);
        return Map.of(
                "organisationId", orgId,
                "periodDays", days,
                "totalAiTokens", totalTokens
        );
    }

    public Map<String, Object> aiUsage(int days) {
        UUID orgId = SecurityUtils.currentOrgId();
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        long embeddingCount = auditLogRepository.countByOrgAndOperationSince(orgId, AiOperation.EMBEDDING, since);
        long rankingCount = auditLogRepository.countByOrgAndOperationSince(orgId, AiOperation.RANKING, since);
        long outreachCount = auditLogRepository.countByOrgAndOperationSince(orgId, AiOperation.OUTREACH, since);
        long totalTokens = auditLogRepository.sumTokensSince(orgId, since);
        return Map.of(
                "organisationId", orgId,
                "periodDays", days,
                "embeddingCalls", embeddingCount,
                "rankingCalls", rankingCount,
                "outreachCalls", outreachCount,
                "totalTokens", totalTokens
        );
    }
}
