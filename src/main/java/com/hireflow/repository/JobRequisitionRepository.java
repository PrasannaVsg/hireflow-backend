package com.hireflow.repository;

import com.hireflow.domain.JobRequisition;
import com.hireflow.domain.enums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JobRequisitionRepository extends JpaRepository<JobRequisition, UUID> {

    Page<JobRequisition> findByOrganisationId(UUID organisationId, Pageable pageable);

    Page<JobRequisition> findByOrganisationIdAndStatus(UUID organisationId,
                                                       JobStatus status,
                                                       Pageable pageable);

    Optional<JobRequisition> findByIdAndOrganisationId(UUID id, UUID organisationId);

    @Modifying
    @Query(value = "update job_requisitions set embedding = cast(:embedding as vector), " +
                   "updated_at = now() where id = :id", nativeQuery = true)
    void updateEmbedding(@Param("id") UUID id, @Param("embedding") String embedding);

    @Query(value = "select embedding is not null from job_requisitions where id = :id", nativeQuery = true)
    Boolean hasEmbedding(@Param("id") UUID id);

    @Query(value = "select cast(embedding as text) from job_requisitions where id = :id", nativeQuery = true)
    String getEmbeddingLiteral(@Param("id") UUID id);
}
