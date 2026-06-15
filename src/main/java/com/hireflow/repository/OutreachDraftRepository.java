package com.hireflow.repository;

import com.hireflow.domain.OutreachDraft;
import com.hireflow.domain.enums.OutreachStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OutreachDraftRepository extends JpaRepository<OutreachDraft, UUID> {

    Page<OutreachDraft> findByCandidateId(UUID candidateId, Pageable pageable);

    Page<OutreachDraft> findByJobIdAndStatus(UUID jobId, OutreachStatus status, Pageable pageable);

    Optional<OutreachDraft> findByIdAndCreatedById(UUID id, UUID createdById);

    @EntityGraph(attributePaths = {"candidate", "job"})
    Page<OutreachDraft> findByCandidateOrganisationId(UUID organisationId, Pageable pageable);
}
