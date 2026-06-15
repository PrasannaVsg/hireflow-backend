package com.hireflow.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "organisations",
       uniqueConstraints = @UniqueConstraint(name = "uk_org_slug", columnNames = "slug"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class Organisation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @ToString.Include
    private UUID id;

    @NotBlank
    @Size(max = 200)
    @Column(name = "name", nullable = false, length = 200)
    @ToString.Include
    private String name;

    @NotBlank
    @Size(max = 80)
    @Column(name = "slug", nullable = false, length = 80)
    private String slug;

    @Column(name = "plan_tier", nullable = false, length = 40)
    @Builder.Default
    private String planTier = "STANDARD";

    @Column(name = "monthly_ai_quota", nullable = false)
    @Builder.Default
    private int monthlyAiQuota = 5000;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "mail_from", length = 200)
    private String mailFrom;

    @Column(name = "mail_reply_to", length = 200)
    private String mailReplyTo;
}
