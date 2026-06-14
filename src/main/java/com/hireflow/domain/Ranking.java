package com.hireflow.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "rankings",
       uniqueConstraints = @UniqueConstraint(name = "uk_rank_job_cand",
                                             columnNames = {"job_id", "candidate_id"}),
       indexes = {
           @Index(name = "idx_rank_job", columnList = "job_id"),
           @Index(name = "idx_rank_score", columnList = "score")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class Ranking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @ToString.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_rank_job"))
    private JobRequisition job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_rank_candidate"))
    private Candidate candidate;

    @NotNull
    @Column(name = "score", nullable = false, precision = 7, scale = 4)
    @ToString.Include
    private BigDecimal score;

    @Column(name = "vector_similarity", precision = 9, scale = 8)
    private BigDecimal vectorSimilarity;

    @Column(name = "llm_score")
    private Integer llmScore;

    @Column(name = "rationale", columnDefinition = "text")
    private String rationale;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skill_breakdown", columnDefinition = "jsonb")
    private String skillBreakdown;

    @Column(name = "model", length = 60)
    private String model;

    @Column(name = "claude_error", columnDefinition = "text")
    private String claudeError;
}
