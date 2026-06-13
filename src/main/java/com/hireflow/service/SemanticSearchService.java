package com.hireflow.service;

import com.hireflow.repository.CandidateRepository;
import com.hireflow.repository.CandidateRepository.SemanticMatch;
import com.hireflow.repository.JobRequisitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchService {

    private final EmbeddingService embeddingService;
    private final CandidateRepository candidateRepository;
    private final JobRequisitionRepository jobRepository;

    @Transactional(readOnly = true)
    public List<SemanticMatch> search(String queryText, UUID jobId, int topK) {
        UUID orgId = SecurityUtils.currentOrgId();
        UUID actorId = SecurityUtils.currentUserId();

        float[] queryVector = embeddingService.embed(queryText, orgId, actorId, null);
        String literal = embeddingService.toVectorLiteral(queryVector);

        int safeK = Math.min(Math.max(topK, 1), 200);
        List<SemanticMatch> matches = candidateRepository
                .findTopKBySimilarity(orgId, jobId, literal, safeK);

        log.debug("Semantic search org={} job={} returned {} matches", orgId, jobId, matches.size());
        return matches;
    }

    @Transactional(readOnly = true)
    public List<SemanticMatch> searchByVector(float[] queryVector, UUID orgId, UUID jobId, int topK) {
        String literal = embeddingService.toVectorLiteral(queryVector);
        int safeK = Math.min(Math.max(topK, 1), 200);
        return candidateRepository.findTopKBySimilarity(orgId, jobId, literal, safeK);
    }

    @Transactional(readOnly = true)
    public List<SemanticMatch> searchByVector(UUID jobId, UUID orgId, int topK) {
        String literal = jobRepository.getEmbeddingLiteral(jobId);
        int safeK = Math.min(Math.max(topK, 1), 200);
        return candidateRepository.findTopKBySimilarity(orgId, jobId, literal, safeK);
    }
}
