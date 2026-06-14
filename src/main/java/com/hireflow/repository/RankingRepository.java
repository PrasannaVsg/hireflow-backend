package com.hireflow.repository;

import com.hireflow.domain.Ranking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RankingRepository extends JpaRepository<Ranking, UUID> {

    @Query("select r from Ranking r " +
           "join fetch r.candidate c " +
           "where r.job.id = :jobId order by r.score desc")
    Page<Ranking> findByJobIdOrderByScoreDesc(@Param("jobId") UUID jobId, Pageable pageable);

    Optional<Ranking> findByJobIdAndCandidateId(UUID jobId, UUID candidateId);

    void deleteByJobId(UUID jobId);

    void deleteByJobIdAndCandidateId(UUID jobId, UUID candidateId);
}
