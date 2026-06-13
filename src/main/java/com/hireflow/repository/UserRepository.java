package com.hireflow.repository;

import com.hireflow.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("select u from User u join fetch u.organisation where u.email = :email")
    Optional<User> findByEmailWithOrg(@Param("email") String email);

    boolean existsByEmail(String email);

    Page<User> findByOrganisationId(UUID organisationId, Pageable pageable);

    Optional<User> findByIdAndOrganisationId(UUID id, UUID organisationId);
}
