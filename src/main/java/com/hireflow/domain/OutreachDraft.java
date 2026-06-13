package com.hireflow.domain;

import com.hireflow.domain.enums.OutreachStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outreach_drafts",
       indexes = {
           @Index(name = "idx_outreach_cand", columnList = "candidate_id"),
           @Index(name = "idx_outreach_status", columnList = "status")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class OutreachDraft extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @ToString.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_outreach_candidate"))
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_outreach_job"))
    private JobRequisition job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false,
                foreignKey = @ForeignKey(name = "fk_outreach_creator"))
    private User createdBy;

    @NotBlank
    @Size(max = 250)
    @Column(name = "subject", nullable = false, length = 250)
    private String subject;

    @NotBlank
    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @NotBlank
    @Column(name = "channel", nullable = false, length = 20)
    @Builder.Default
    private String channel = "EMAIL";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OutreachStatus status = OutreachStatus.DRAFT;

    @Column(name = "model", length = 60)
    private String model;

    @Column(name = "sent_at")
    private Instant sentAt;
}
