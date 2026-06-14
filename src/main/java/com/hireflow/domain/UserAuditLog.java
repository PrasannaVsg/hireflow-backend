package com.hireflow.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_audit_log",
       indexes = {
           @Index(name = "idx_ual_org",     columnList = "organisation_id"),
           @Index(name = "idx_ual_user",    columnList = "user_id"),
           @Index(name = "idx_ual_created", columnList = "created_at"),
           @Index(name = "idx_ual_action",  columnList = "action")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_name", length = 120)
    private String userName;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "entity_type", length = 30)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "entity_name", length = 200)
    private String entityName;

    @Column(name = "details", columnDefinition = "text")
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
