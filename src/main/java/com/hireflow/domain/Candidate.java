package com.hireflow.domain;

import com.hireflow.domain.enums.CandidateSource;
import com.hireflow.domain.enums.CandidateStatus;
import com.hireflow.domain.enums.PipelineStage;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "candidates",
       indexes = {
           @Index(name = "idx_cand_org", columnList = "organisation_id"),
           @Index(name = "idx_cand_job", columnList = "job_id"),
           @Index(name = "idx_cand_stage", columnList = "pipeline_stage")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class Candidate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @ToString.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_cand_org"))
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", foreignKey = @ForeignKey(name = "fk_cand_job"))
    private JobRequisition job;

    @NotBlank
    @Size(max = 160)
    @Column(name = "full_name", nullable = false, length = 160)
    @ToString.Include
    private String fullName;

    @Email
    @Size(max = 255)
    @Column(name = "email", length = 255)
    private String email;

    @Size(max = 40)
    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "resume_object_key", length = 512)
    private String resumeObjectKey;

    @Column(name = "resume_text", columnDefinition = "text")
    private String resumeText;

    @Transient
    private float[] embedding;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CandidateStatus status = CandidateStatus.NEW;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "pipeline_stage", nullable = false, length = 20)
    @Builder.Default
    @ToString.Include
    private PipelineStage pipelineStage = PipelineStage.SOURCED;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20)
    private CandidateSource source;
}
