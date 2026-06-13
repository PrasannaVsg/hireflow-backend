package com.hireflow.domain;

import com.hireflow.domain.enums.AiOperation;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "ai_audit_log",
       indexes = {
           @Index(name = "idx_audit_org", columnList = "organisation_id"),
           @Index(name = "idx_audit_op", columnList = "operation"),
           @Index(name = "idx_audit_created", columnList = "created_at")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class AiAuditLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @ToString.Include
    private UUID id;

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 20)
    @ToString.Include
    private AiOperation operation;

    @Column(name = "model", length = 60)
    private String model;

    @Column(name = "request_tokens")
    private Integer requestTokens;

    @Column(name = "response_tokens")
    private Integer responseTokens;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "success", nullable = false)
    @Builder.Default
    private boolean success = true;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "prompt_hash", length = 64)
    private String promptHash;

    @Column(name = "target_entity_id")
    private UUID targetEntityId;
}
