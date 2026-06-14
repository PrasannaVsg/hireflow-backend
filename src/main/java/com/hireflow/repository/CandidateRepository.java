package com.hireflow.repository;

import com.hireflow.domain.Candidate;
import com.hireflow.domain.enums.CandidateStatus;
import com.hireflow.domain.enums.PipelineStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateRepository extends JpaRepository<Candidate, UUID>, JpaSpecificationExecutor<Candidate> {

    Page<Candidate> findByOrganisationId(UUID organisationId, Pageable pageable);

    Page<Candidate> findByJobIdAndOrganisationId(UUID jobId, UUID organisationId, Pageable pageable);

    Page<Candidate> findByJobIdAndPipelineStage(UUID jobId, PipelineStage stage, Pageable pageable);

    Optional<Candidate> findByIdAndOrganisationId(UUID id, UUID organisationId);

    long countByOrganisationIdAndStatus(UUID organisationId, CandidateStatus status);

    long countByOrganisationIdAndPipelineStage(UUID organisationId, PipelineStage stage);

    long countByJobId(UUID jobId);

    @Query(value = "select count(*) from candidates where job_id = :jobId and embedding is not null", nativeQuery = true)
    long countEmbeddedByJobId(@Param("jobId") UUID jobId);

    @Modifying
    @Transactional
    @Query("UPDATE Candidate c SET c.status = com.hireflow.domain.enums.CandidateStatus.ARCHIVED WHERE c.job.id = :jobId")
    int deactivateByJobId(@Param("jobId") UUID jobId);

    @Modifying
    @Transactional
    @Query(value = "update candidates set embedding = cast(:embedding as vector), " +
                   "status = 'ACTIVE', updated_at = now() where id = :id", nativeQuery = true)
    void updateEmbedding(@Param("id") UUID id, @Param("embedding") String embedding);

    @Query(value = """
            select c.id              as candidateId,
                   c.full_name       as fullName,
                   (1 - (c.embedding <=> cast(:queryVector as vector))) as similarity
            from candidates c
            where c.organisation_id = :orgId
              and c.embedding is not null
              and (:jobId is null or c.job_id = :jobId)
            order by c.embedding <=> cast(:queryVector as vector)
            limit :topK
            """, nativeQuery = true)
    List<SemanticMatch> findTopKBySimilarity(@Param("orgId") UUID orgId,
                                             @Param("jobId") UUID jobId,
                                             @Param("queryVector") String queryVector,
                                             @Param("topK") int topK);

    interface SemanticMatch {
        UUID getCandidateId();
        String getFullName();
        double getSimilarity();
    }
}
