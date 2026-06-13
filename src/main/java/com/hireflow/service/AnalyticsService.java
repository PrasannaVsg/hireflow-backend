package com.hireflow.service;

import com.hireflow.domain.AiAuditLog;
import com.hireflow.domain.enums.AiOperation;
import com.hireflow.domain.enums.CandidateStatus;
import com.hireflow.domain.enums.JobStatus;
import com.hireflow.domain.enums.PipelineStage;
import com.hireflow.repository.AiAuditLogRepository;
import com.hireflow.repository.CandidateRepository;
import com.hireflow.repository.JobRequisitionRepository;
import com.hireflow.repository.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final AiAuditLogRepository auditLogRepository;
    private final JobRequisitionRepository jobRepository;
    private final CandidateRepository candidateRepository;
    private final RankingRepository rankingRepository;

    public Map<String, Object> dashboard(int days) {
        UUID orgId = SecurityUtils.currentOrgId();
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        // Top cards
        long openJobs = jobRepository.countByOrganisationIdAndStatus(orgId, JobStatus.OPEN);
        long activeCandidates = candidateRepository.countByOrganisationIdAndStatus(orgId, CandidateStatus.ACTIVE);
        long rankingsRun = auditLogRepository.countByOrgAndOperationSince(orgId, AiOperation.RANKING, since);
        long outreachDrafted = auditLogRepository.countByOrgAndOperationSince(orgId, AiOperation.OUTREACH, since);

        // Hiring funnel — candidates by pipeline stage
        Map<String, Long> funnel = new LinkedHashMap<>();
        for (PipelineStage stage : PipelineStage.values()) {
            funnel.put(stage.name(), candidateRepository.countByOrganisationIdAndPipelineStage(orgId, stage));
        }

        // AI token usage by operation
        long rankingTokens   = auditLogRepository.sumTokensByOperationSince(orgId, AiOperation.RANKING, since);
        long outreachTokens  = auditLogRepository.sumTokensByOperationSince(orgId, AiOperation.OUTREACH, since);
        long embeddingTokens = auditLogRepository.sumTokensByOperationSince(orgId, AiOperation.EMBEDDING, since);
        long totalTokens     = rankingTokens + outreachTokens + embeddingTokens;

        Map<String, Object> aiUsage = Map.of(
                "totalTokens", totalTokens,
                "byOperation", Map.of(
                        "RANKING", rankingTokens,
                        "OUTREACH", outreachTokens,
                        "EMBEDDING", embeddingTokens
                )
        );

        // Recent AI activity
        List<AiAuditLog> recentLogs = auditLogRepository.findRecentByOrg(orgId, PageRequest.of(0, 10));
        List<Map<String, Object>> recentActivity = recentLogs.stream().map(log -> Map.<String, Object>of(
                "operation", log.getOperation().name(),
                "model", log.getModel() != null ? log.getModel() : "",
                "success", log.isSuccess(),
                "latencyMs", log.getLatencyMs(),
                "createdAt", log.getCreatedAt().toString()
        )).toList();

        return Map.of(
                "periodDays", days,
                "openRequisitions", openJobs,
                "activeCandidates", activeCandidates,
                "aiRankingsRun", rankingsRun,
                "outreachDrafted", outreachDrafted,
                "hiringFunnel", funnel,
                "aiUsage", aiUsage,
                "recentActivity", recentActivity
        );
    }

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
        long rankingCount   = auditLogRepository.countByOrgAndOperationSince(orgId, AiOperation.RANKING, since);
        long outreachCount  = auditLogRepository.countByOrgAndOperationSince(orgId, AiOperation.OUTREACH, since);
        long totalTokens    = auditLogRepository.sumTokensSince(orgId, since);
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
