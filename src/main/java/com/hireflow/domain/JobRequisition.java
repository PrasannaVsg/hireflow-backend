package com.hireflow.domain;

import com.hireflow.domain.enums.JobStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "job_requisitions",
       indexes = {
           @Index(name = "idx_job_org",    columnList = "organisation_id"),
           @Index(name = "idx_job_status", columnList = "status")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class JobRequisition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @ToString.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_job_org"))
    private Organisation organisation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false,
                foreignKey = @ForeignKey(name = "fk_job_creator"))
    private User createdBy;

    @Column(name = "job_code", length = 40)
    private String jobCode;

    @NotBlank
    @Size(max = 200)
    @Column(name = "title", nullable = false, length = 200)
    @ToString.Include
    private String title;

    @Size(max = 200)
    @Column(name = "client_name", length = 200)
    private String clientName;

    @NotBlank
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    /** Comma-separated list of locations e.g. "Bangalore,Chennai,Remote" */
    @Column(name = "locations", columnDefinition = "text")
    private String locations;

    @Column(name = "seniority", length = 60)
    private String seniority;

    @Column(name = "exp_min")
    private Integer expMin;

    @Column(name = "exp_max")
    private Integer expMax;

    @Column(name = "required_skills", columnDefinition = "text")
    private String requiredSkills;

    @Column(name = "budget_min", precision = 12, scale = 2)
    private BigDecimal budgetMin;

    @Column(name = "budget_max", precision = 12, scale = 2)
    private BigDecimal budgetMax;

    @Column(name = "mail_template", columnDefinition = "text")
    private String mailTemplate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    @ToString.Include
    private JobStatus status = JobStatus.DRAFT;

    @Transient
    private float[] embedding;

    @Column(name = "auto_process_enabled", nullable = false)
    @Builder.Default
    private boolean autoProcessEnabled = false;

    @Column(name = "auto_email_on_stage_change", nullable = false)
    @Builder.Default
    private boolean autoEmailOnStageChange = false;

    @Column(name = "auto_shortlist_size", nullable = false)
    @Builder.Default
    private int autoShortlistSize = 25;

    @Column(name = "auto_score_threshold", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal autoScoreThreshold = BigDecimal.valueOf(60.0);

    @Column(name = "auto_email_tone", length = 40)
    @Builder.Default
    private String autoEmailTone = "professional";
}
