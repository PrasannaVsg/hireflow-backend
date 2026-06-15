package com.hireflow.web.controller;

import com.hireflow.service.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final EntityManager em;

    public record JobReport(
            UUID jobId,
            String jobCode,
            String title,
            String clientName,
            BigDecimal budgetMin,
            BigDecimal budgetMax,
            int totalCandidates,
            int hiredCount,
            int offeredCount,
            BigDecimal avgOfferAmount,
            BigDecimal totalOfferAmount,
            BigDecimal budgetSaving,   // budgetMax - avgOffer (positive = under budget)
            String status
    ) {}

    @SuppressWarnings("unchecked")
    @GetMapping("/jobs")
    public List<JobReport> jobSummary() {
        UUID orgId = SecurityUtils.currentOrgId();

        String sql = """
            SELECT
                j.id                                           AS job_id,
                j.job_code                                     AS job_code,
                j.title                                        AS title,
                j.client_name                                  AS client_name,
                j.budget_min                                   AS budget_min,
                j.budget_max                                   AS budget_max,
                j.status                                       AS status,
                COUNT(c.id)                                    AS total_candidates,
                COUNT(c.id) FILTER (WHERE c.pipeline_stage = 'HIRED')          AS hired_count,
                COUNT(c.id) FILTER (WHERE c.pipeline_stage IN
                    ('FINAL_SELECT','OFFER_RELEASED','HIRED'))                  AS offered_count,
                AVG(c.offer_amount)  FILTER (WHERE c.offer_amount IS NOT NULL
                    AND c.pipeline_stage IN ('FINAL_SELECT','OFFER_RELEASED','HIRED'))
                                                               AS avg_offer,
                SUM(c.offer_amount)  FILTER (WHERE c.offer_amount IS NOT NULL
                    AND c.pipeline_stage IN ('FINAL_SELECT','OFFER_RELEASED','HIRED'))
                                                               AS total_offer
            FROM job_requisitions j
            LEFT JOIN candidates c ON c.job_id = j.id
            WHERE j.organisation_id = :orgId
            GROUP BY j.id, j.job_code, j.title, j.client_name,
                     j.budget_min, j.budget_max, j.status
            ORDER BY j.created_at DESC
            """;

        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("orgId", orgId)
                .getResultList();

        return rows.stream().map(r -> {
            BigDecimal budgetMax  = r[5] != null ? new BigDecimal(r[5].toString()) : null;
            BigDecimal avgOffer   = r[10] != null ? new BigDecimal(r[10].toString()).setScale(0, RoundingMode.HALF_UP) : null;
            BigDecimal saving     = (budgetMax != null && avgOffer != null)
                                    ? budgetMax.subtract(avgOffer) : null;

            return new JobReport(
                (UUID)   r[0],
                         r[1] != null ? r[1].toString() : null,
                         r[2] != null ? r[2].toString() : null,
                         r[3] != null ? r[3].toString() : null,
                r[4] != null ? new BigDecimal(r[4].toString()) : null,
                budgetMax,
                ((Number) r[7]).intValue(),
                ((Number) r[8]).intValue(),
                ((Number) r[9]).intValue(),
                avgOffer,
                r[11] != null ? new BigDecimal(r[11].toString()).setScale(0, RoundingMode.HALF_UP) : null,
                saving,
                r[6] != null ? r[6].toString() : null
            );
        }).toList();
    }
}
