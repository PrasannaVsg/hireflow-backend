package com.hireflow.repository;

import com.hireflow.domain.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganisationRepository extends JpaRepository<Organisation, UUID> {

    Optional<Organisation> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
