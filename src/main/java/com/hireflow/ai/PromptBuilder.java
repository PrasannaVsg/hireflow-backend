package com.hireflow.ai;

import com.hireflow.ai.prompt.PromptTemplates;
import com.hireflow.domain.Candidate;
import com.hireflow.domain.JobRequisition;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    private static final int RESUME_EXCERPT_LIMIT = 12_000;

    public String buildRankingSystemPrompt() {
        return PromptTemplates.RANKING_SYSTEM;
    }

    public String buildRankingUserPrompt(JobRequisition job, Candidate candidate) {
        return PromptTemplates.RANKING_USER.formatted(
                safe(job.getTitle()),
                safe(job.getSeniority()),
                safe(job.getLocation()),
                safe(job.getRequiredSkills()),
                safe(job.getDescription()),
                safe(candidate.getFullName()),
                excerpt(candidate.getResumeText()));
    }

    public String buildOutreachSystemPrompt() {
        return PromptTemplates.OUTREACH_SYSTEM;
    }

    public String buildOutreachUserPrompt(JobRequisition job, Candidate candidate,
                                          String senderName, String tone) {
        return PromptTemplates.OUTREACH_USER.formatted(
                safe(job.getTitle()),
                safe(job.getLocation()),
                safe(job.getDescription()).substring(0, Math.min(safe(job.getDescription()).length(), 600)),
                safe(candidate.getFullName()),
                excerpt(candidate.getResumeText()),
                safe(senderName),
                tone == null || tone.isBlank() ? "professional and friendly" : tone);
    }

    private String excerpt(String text) {
        if (text == null) return "(no resume text)";
        return text.length() > RESUME_EXCERPT_LIMIT ? text.substring(0, RESUME_EXCERPT_LIMIT) : text;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
