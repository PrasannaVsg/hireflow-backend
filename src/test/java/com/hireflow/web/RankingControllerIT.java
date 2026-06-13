package com.hireflow.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireflow.ai.AnthropicClient;
import com.hireflow.ai.dto.RankingResult;
import com.hireflow.domain.*;
import com.hireflow.domain.enums.JobStatus;
import com.hireflow.domain.enums.Role;
import com.hireflow.repository.*;
import com.hireflow.security.JwtUtil;
import com.hireflow.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RankingControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("hireflow").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("hireflow.jwt.secret", () -> "test-secret-key-at-least-32-bytes-long-xx");
        registry.add("hireflow.anthropic.api-key", () -> "test-key");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtUtil jwtUtil;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired OrganisationRepository orgRepo;
    @Autowired UserRepository userRepo;
    @Autowired JobRequisitionRepository jobRepo;
    @Autowired CandidateRepository candidateRepo;

    @MockBean AnthropicClient anthropicClient;

    private String accessToken;
    private UUID jobId;

    @BeforeEach
    void seed() {
        Organisation org = orgRepo.save(Organisation.builder()
                .name("Acme").slug("acme-" + UUID.randomUUID()).build());

        User recruiter = userRepo.save(User.builder()
                .organisation(org).email("rec-" + UUID.randomUUID() + "@acme.io")
                .passwordHash(passwordEncoder.encode("pw")).fullName("Rec")
                .role(Role.RECRUITER).enabled(true).build());

        JobRequisition job = jobRepo.save(JobRequisition.builder()
                .organisation(org).createdBy(recruiter).title("Senior Java Engineer")
                .description("Spring Boot, PostgreSQL, distributed systems")
                .status(JobStatus.OPEN).build());
        jobRepo.updateEmbedding(job.getId(), vectorLiteral());
        this.jobId = job.getId();

        Candidate cand = candidateRepo.save(Candidate.builder()
                .organisation(org).job(job).fullName("Grace Hopper")
                .resumeText("10 years Java, Spring Boot, PostgreSQL").build());
        candidateRepo.updateEmbedding(cand.getId(), vectorLiteral());

        accessToken = jwtUtil.generateAccessToken(new CustomUserDetails(recruiter));

        when(anthropicClient.modelId()).thenReturn("claude-sonnet-4-6");
        when(anthropicClient.completeForJson(any(), any(), eq(RankingResult.class)))
                .thenReturn(new RankingResult(88, "Strong Spring Boot match", null, 120, 60));
    }

    @Test
    void runRanking_returnsRankedCandidates_forRecruiter() throws Exception {
        mockMvc.perform(post("/api/v1/jobs/{jobId}/rankings/run", jobId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("shortlistSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].candidateName").value("Grace Hopper"))
                .andExpect(jsonPath("$[0].llmScore").value(88))
                .andExpect(jsonPath("$[0].score").exists());
    }

    @Test
    void runRanking_succeedsWithoutToken_whenAuthDeferred() throws Exception {
        // Auth is deferred (permitAll); endpoint still works without token
        mockMvc.perform(post("/api/v1/jobs/{jobId}/rankings/run", jobId)
                        .param("shortlistSize", "10"))
                .andExpect(status().isOk());
    }

    private static String vectorLiteral() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 1536; i++) {
            if (i > 0) sb.append(',');
            sb.append(i == 0 ? "1.0" : "0.0");
        }
        return sb.append(']').toString();
    }
}
