# HireFlow Backend

AI-powered recruiting platform backend that automates resume ranking, candidate shortlisting, and outreach email generation using Claude AI and Voyage AI embeddings.

---

## Table of Contents

- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [AI Integrations](#ai-integrations)
- [Prerequisites](#prerequisites)
- [Local Setup](#local-setup)
- [Environment Variables](#environment-variables)
- [Running the Application](#running-the-application)
- [API Reference](#api-reference)
- [Application Flow](#application-flow)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        REST API Layer                        │
│          (Spring Boot Controllers + Swagger UI)              │
└────────────────────────┬────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────┐
│                      Service Layer                           │
│   JobService │ CandidateService │ RankingService             │
│   OutreachService │ AutoProcessService │ EmbeddingService     │
└──────┬──────────────┬──────────────────┬────────────────────┘
       │              │                  │
┌──────▼──────┐ ┌─────▼──────┐  ┌───────▼────────┐
│  PostgreSQL  │ │   MinIO    │  │   Voyage AI    │
│ + pgvector  │ │  (Resumes) │  │  (Embeddings)  │
└─────────────┘ └────────────┘  └───────┬────────┘
                                         │
                                ┌────────▼────────┐
                                │  Claude Sonnet  │
                                │  (Ranking +     │
                                │   Outreach)     │
                                └─────────────────┘
       │
┌──────▼──────┐
│    Redis    │
│ (Async job  │
│  tracking)  │
└─────────────┘
```

### Connecting Points

| Component | Purpose | Connection |
|---|---|---|
| PostgreSQL + pgvector | Store all data + vector similarity search | JDBC via Spring Data JPA |
| MinIO | Store uploaded resume files | S3-compatible HTTP client |
| Redis | Track async batch upload job status | Spring Data Redis |
| Voyage AI | Convert text to embedding vectors | HTTP REST API |
| Claude Sonnet | Rank candidates + draft outreach emails | HTTP REST API |
| Spring Mail | Send outreach emails via SMTP | JavaMailSender |

---

## Tech Stack

- **Java 21**
- **Spring Boot 3.2.5**
- **PostgreSQL 16** with **pgvector** extension
- **Redis 7**
- **MinIO** (S3-compatible local storage)
- **Apache Tika** (resume text extraction from PDF/Word)
- **Flyway** (database migrations)
- **MapStruct** (DTO mapping)
- **SpringDoc OpenAPI** (Swagger UI)
- **Docker Compose** (local infrastructure)

---

## AI Integrations

### 1. Voyage AI — Embeddings
- **URL:** `https://api.voyageai.com/v1/embeddings`
- **Model:** `voyage-2` (1024 dimensions)
- **API Key:** Voyage AI key (`pa-...`)
- **Purpose:** Converts job descriptions and resume text into numerical vectors (embeddings). These vectors are stored in PostgreSQL using pgvector. When ranking is triggered, the job embedding is compared against all candidate embeddings using cosine similarity to find the most relevant candidates — before sending them to Claude. This reduces Claude API calls and cost.
- **Called when:**
  - Job status changes to `OPEN` → job description is embedded
  - Resume is uploaded → resume text is embedded

### 2. Anthropic Claude Sonnet — LLM
- **URL:** `https://api.anthropic.com/v1/messages`
- **Model:** `claude-sonnet-4-6`
- **API Key:** Anthropic key (`sk-ant-api03-...`)
- **Purpose:**
  - **Candidate Ranking:** Reads job description + resume text, scores the candidate (0–100) with rationale and skill breakdown
  - **Outreach Email:** Generates a personalized recruitment email for a candidate based on job and resume
- **Called when:**
  - `POST /api/v1/jobs/{jobId}/rankings/run` — ranking
  - `POST /api/v1/outreach` — email drafting
  - `POST /api/v1/jobs/{jobId}/auto-process` — both

---

## Prerequisites

Install the following before setup:

- [Java 21](https://adoptium.net/)
- [Maven 3.9+](https://maven.apache.org/)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/)
- [Git](https://git-scm.com/)
- Anthropic API Key → [console.anthropic.com](https://console.anthropic.com)
- Voyage AI API Key → [dashboard.voyageai.com](https://dashboard.voyageai.com)

---

## Local Setup

### Step 1: Clone the repository

```bash
git clone https://github.com/PrasannaVsg/hireflow-backend.git
cd hireflow-backend
```

### Step 2: Start infrastructure with Docker

```bash
docker-compose up -d postgres redis minio
```

This starts:
- PostgreSQL on port `5432`
- Redis on port `6379`
- MinIO on port `9000` (UI on `9001`)

Verify all containers are running:
```bash
docker ps
```

### Step 3: Create MinIO bucket

1. Open `http://localhost:9001` in browser
2. Login: username `minioadmin` / password `minioadmin`
3. Click **Create Bucket**
4. Bucket name: `hireflow-resumes`
5. Click **Create**

### Step 4: Set environment variables

In your IDE (Eclipse/IntelliJ), set these environment variables in the Run Configuration:

```
SPRING_PROFILES_ACTIVE=dev
ANTHROPIC_API_KEY=sk-ant-api03-your-key-here
VOYAGE_API_KEY=pa-your-voyage-key-here
```

### Step 5: Run the application

In Eclipse:
1. Right-click `HireFlowApplication.java` → **Run As** → **Java Application**
2. Or: **Run Configurations** → set environment variables → **Run**

In IntelliJ:
1. Open `HireFlowApplication.java`
2. Click the green Run button
3. Set env vars in **Edit Configurations** → **Environment variables**

Via Maven:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Step 6: Verify startup

Application starts on: `http://localhost:8080`

Swagger UI: `http://localhost:8080/swagger-ui.html`

Health check: `http://localhost:8080/actuator/health`

---

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Yes | Use `dev` for local |
| `ANTHROPIC_API_KEY` | Yes | Claude AI key from console.anthropic.com |
| `VOYAGE_API_KEY` | Yes | Voyage AI key from dashboard.voyageai.com |
| `MAIL_HOST` | No | SMTP host (default: smtp.gmail.com) |
| `MAIL_PORT` | No | SMTP port (default: 587) |
| `MAIL_USERNAME` | No | Email address for sending outreach |
| `MAIL_PASSWORD` | No | Email app password |
| `MAIL_FROM` | No | From address (default: noreply@hireflow.io) |

---

## API Reference

Base URL: `http://localhost:8080/api/v1`

---

### Authentication

#### Login
```
POST /api/v1/auth/login
Content-Type: application/json
```
Request:
```json
{
  "email": "admin@hireflow.io",
  "password": "changeme"
}
```
Response:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 900
}
```

#### Refresh Token
```
POST /api/v1/auth/refresh
Content-Type: application/json
```
Request:
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

#### Logout
```
POST /api/v1/auth/logout
```

---

### Users

#### Create User
```
POST /api/v1/users
Content-Type: application/json
```
Request:
```json
{
  "fullName": "Sam Recruiter",
  "email": "sam@hireflow.io",
  "password": "securepassword",
  "role": "RECRUITER"
}
```
Response:
```json
{
  "id": "user-uuid",
  "fullName": "Sam Recruiter",
  "email": "sam@hireflow.io",
  "role": "RECRUITER",
  "createdAt": "2026-06-13T10:00:00Z"
}
```

#### List Users
```
GET /api/v1/users?page=0&size=20
```

#### Disable User
```
PATCH /api/v1/users/{userId}/disable
```

---

### Jobs

#### Create Job
```
POST /api/v1/jobs
Content-Type: application/json
```
Request:
```json
{
  "title": "Senior Java Engineer",
  "description": "We need a Java developer with Spring Boot, PostgreSQL and REST API experience. 5+ years required.",
  "location": "Bangalore",
  "seniority": "Senior",
  "requiredSkills": "Java, Spring Boot, PostgreSQL, REST API",
  "autoProcessEnabled": false,
  "shortlistSize": 25,
  "scoreThreshold": 60.0,
  "emailTone": "professional"
}
```
Response:
```json
{
  "id": "0a6527e4-1809-4cdc-8199-6959391eb42c",
  "title": "Senior Java Engineer",
  "description": "We need a Java developer...",
  "location": "Bangalore",
  "seniority": "Senior",
  "status": "DRAFT",
  "autoProcessEnabled": false,
  "createdAt": "2026-06-13T10:00:00Z"
}
```

#### Publish Job (triggers embedding via Voyage AI)
```
PATCH /api/v1/jobs/{jobId}/status?status=OPEN
```
Response:
```json
{
  "id": "0a6527e4-1809-4cdc-8199-6959391eb42c",
  "status": "OPEN"
}
```

#### Get Job by ID
```
GET /api/v1/jobs/{jobId}
```
Response:
```json
{
  "id": "0a6527e4-1809-4cdc-8199-6959391eb42c",
  "title": "Senior Java Engineer",
  "description": "We need a Java developer...",
  "location": "Bangalore",
  "seniority": "Senior",
  "status": "OPEN",
  "autoProcessEnabled": false,
  "shortlistSize": 25,
  "scoreThreshold": 60.0,
  "createdAt": "2026-06-13T10:00:00Z"
}
```

#### Update Job
```
PUT /api/v1/jobs/{jobId}
Content-Type: application/json
```
Request:
```json
{
  "title": "Senior Java Engineer (Updated)",
  "description": "Updated job description...",
  "location": "Remote",
  "seniority": "Senior",
  "requiredSkills": "Java, Spring Boot, Kubernetes",
  "shortlistSize": 30,
  "scoreThreshold": 65.0
}
```

#### Reindex Job Embedding
```
POST /api/v1/jobs/{jobId}/reindex
```
> Re-embeds the job description via Voyage AI. Use after editing job description.

Response:
```json
{
  "id": "0a6527e4-1809-4cdc-8199-6959391eb42c",
  "message": "Job reindexed successfully"
}
```

#### Delete Job
```
DELETE /api/v1/jobs/{jobId}
```

#### List Jobs
```
GET /api/v1/jobs?page=0&size=20
```

---

### Candidates

#### Create Single Candidate
```
POST /api/v1/candidates
Content-Type: application/json
```
Request:
```json
{
  "fullName": "Jane Doe",
  "email": "jane.doe@example.com",
  "phone": "+91-9876543210",
  "jobId": "0a6527e4-1809-4cdc-8199-6959391eb42c",
  "source": "LINKEDIN",
  "resumeText": "Jane Doe is a Senior Java Developer with 7 years of experience in Spring Boot, PostgreSQL, and microservices..."
}
```
Response:
```json
{
  "id": "candidate-uuid",
  "fullName": "Jane Doe",
  "email": "jane.doe@example.com",
  "status": "ACTIVE",
  "pipelineStage": "SOURCED",
  "createdAt": "2026-06-13T10:00:00Z"
}
```

#### Get Candidate by ID
```
GET /api/v1/candidates/{candidateId}
```
Response:
```json
{
  "id": "candidate-uuid",
  "fullName": "Jane Doe",
  "email": "jane.doe@example.com",
  "phone": "+91-9876543210",
  "source": "LINKEDIN",
  "status": "ACTIVE",
  "pipelineStage": "SOURCED",
  "resumeText": "Jane Doe is a Senior Java Developer...",
  "jobId": "0a6527e4-1809-4cdc-8199-6959391eb42c",
  "createdAt": "2026-06-13T10:00:00Z"
}
```

#### Get Resume Download URL
```
GET /api/v1/candidates/{candidateId}/resume-url
```
Response:
```json
{
  "url": "http://localhost:9000/hireflow-resumes/candidate-uuid/resume.pdf?X-Amz-Expires=3600&...",
  "expiresIn": 3600
}
```

#### Bulk Upload Resumes
```
POST /api/v1/candidates/batch-upload
Content-Type: multipart/form-data
```
Form fields:
| Field | Type | Value |
|---|---|---|
| `files` | File | resume.pdf or resume.docx |
| `jobId` | Text | `0a6527e4-1809-4cdc-8199-6959391eb42c` |
| `source` | Text | `LINKEDIN` / `NAUKRI` / `INDEED` / `INTERNSHALA` / `REFERRAL` / `DIRECT` / `OTHER` |

Response:
```json
{
  "jobId": "4983f7e7-e66a-4b58-a939-a9c74cc1b7b9",
  "status": "QUEUED",
  "total": 1
}
```

#### Check Upload Status
```
GET /api/v1/candidates/batch-upload/{jobId}/status
```
Response:
```json
{
  "jobId": "4983f7e7-e66a-4b58-a939-a9c74cc1b7b9",
  "state": "COMPLETED",
  "total": 1,
  "succeeded": 1,
  "failed": 0
}
```

#### List Candidates for a Job
```
GET /api/v1/candidates?jobId={jobId}&page=0&size=20
```

#### Move Pipeline Stage
```
PATCH /api/v1/candidates/{candidateId}/stage?stage=SCREENING
```
Stages: `SOURCED` → `SCREENING` → `INTERVIEW` → `OFFER` → `HIRED` / `REJECTED`

Response:
```json
{
  "id": "candidate-uuid",
  "fullName": "John Smith",
  "pipelineStage": "SCREENING"
}
```

---

### Rankings

#### Run Ranking (calls Claude AI)
```
POST /api/v1/jobs/{jobId}/rankings/run?shortlistSize=10
```
Response:
```json
[
  {
    "candidateId": "candidate-uuid",
    "candidateName": "John Smith",
    "score": 78.5000,
    "vectorSimilarity": 0.85432100,
    "llmScore": 82,
    "rationale": "Strong match for Java and Spring Boot requirements. 6 years of relevant experience with PostgreSQL and microservices. Missing Kubernetes experience.",
    "skillBreakdown": "{\"Java\": 90, \"Spring Boot\": 85, \"PostgreSQL\": 80}",
    "model": "claude-sonnet-4-6"
  }
]
```

#### View Rankings (no AI call — reads from DB)
```
GET /api/v1/jobs/{jobId}/rankings?page=0&size=20
```

---

### Outreach Emails

#### Generate Outreach Email (calls Claude AI)
```
POST /api/v1/outreach/draft
Content-Type: application/json
```
Request:
```json
{
  "candidateId": "candidate-uuid",
  "jobId": "0a6527e4-1809-4cdc-8199-6959391eb42c",
  "tone": "professional"
}
```
Response:
```json
{
  "id": "draft-uuid",
  "subject": "Exciting Senior Java Engineer Opportunity",
  "body": "Dear John,\n\nI came across your profile and was impressed by your Java and Spring Boot experience...",
  "status": "DRAFT",
  "createdAt": "2026-06-13T10:00:00Z"
}
```

#### Update Outreach Status
```
PATCH /api/v1/outreach/{draftId}/status?status=APPROVED
```
Statuses: `DRAFT` → `APPROVED` → `SENT` / `REJECTED`

#### Send Email (requires SMTP config)
```
POST /api/v1/outreach/{draftId}/send
```
Response:
```json
{
  "id": "draft-uuid",
  "status": "SENT",
  "sentAt": "2026-06-13T10:05:00Z"
}
```

---

### Analytics

#### Dashboard (for UI prototype)
```
GET /api/v1/analytics/dashboard?days=30
```
Response:
```json
{
  "periodDays": 30,
  "openRequisitions": 5,
  "activeCandidates": 42,
  "aiRankingsRun": 12,
  "outreachDrafted": 30,
  "hiringFunnel": {
    "SOURCED": 42,
    "SCREENING": 18,
    "INTERVIEW": 8,
    "OFFER": 3,
    "HIRED": 2,
    "REJECTED": 11
  },
  "aiUsage": {
    "totalTokens": 185000,
    "byOperation": {
      "RANKING": 95000,
      "OUTREACH": 72000,
      "EMBEDDING": 18000
    }
  },
  "recentActivity": [
    {
      "operation": "RANKING",
      "model": "claude-sonnet-4-6",
      "success": true,
      "latencyMs": 3200,
      "createdAt": "2026-06-13T09:45:00Z"
    }
  ]
}
```

#### Overview
```
GET /api/v1/analytics/overview?days=30
```
Response:
```json
{
  "organisationId": "org-uuid",
  "periodDays": 30,
  "totalAiTokens": 185000
}
```

#### AI Usage Breakdown
```
GET /api/v1/analytics/ai-usage?days=30
```
Response:
```json
{
  "organisationId": "org-uuid",
  "periodDays": 30,
  "embeddingCalls": 54,
  "rankingCalls": 12,
  "outreachCalls": 30,
  "totalTokens": 185000
}
```

---

### Auto Process (Rank + Shortlist + Email + Pipeline — all in one)

#### Trigger Auto Process
```
POST /api/v1/jobs/{jobId}/auto-process
```
> Requires `autoProcessEnabled: true` on the job

Response:
```json
{
  "jobId": "0a6527e4-1809-4cdc-8199-6959391eb42c",
  "shortlisted": 5,
  "emailsSent": 5,
  "movedToScreening": 5
}
```

#### Get Auto Process Config
```
GET /api/v1/jobs/{jobId}/auto-process/config
```

#### Update Auto Process Config
```
PATCH /api/v1/jobs/{jobId}/auto-process/config
Content-Type: application/json
```
Request:
```json
{
  "enabled": true,
  "shortlistSize": 10,
  "scoreThreshold": 70.0,
  "emailTone": "friendly"
}
```

---

## Application Flow

### Manual Flow (step by step)

```
1. POST /jobs                          → Create job
2. PATCH /jobs/{id}/status?status=OPEN → Publish job (Voyage AI embeds job description)
3. POST /candidates/batch-upload       → Upload resumes (Voyage AI embeds each resume)
4. GET  /candidates/batch-upload/{id}/status → Check upload completed
5. POST /jobs/{id}/rankings/run        → Claude AI ranks candidates
6. GET  /jobs/{id}/rankings            → View ranked results
7. PATCH /candidates/{id}/stage        → Move shortlisted candidates to SCREENING
8. POST /outreach                      → Claude AI drafts email for a candidate
9. POST /outreach/{id}/send            → Send the email
```

### Auto Flow (one API call)

```
1. POST /jobs                          → Create job with autoProcessEnabled: true
2. PATCH /jobs/{id}/status?status=OPEN → Publish job
3. POST /candidates/batch-upload       → Upload resumes
4. POST /jobs/{id}/auto-process        → Everything automated:
                                          - Rank all candidates (Claude)
                                          - Filter by scoreThreshold
                                          - Draft + send emails (Claude)
                                          - Move to SCREENING pipeline
```

### How Ranking Works Internally

```
Resume Upload
    │
    ▼
Voyage AI → candidate embedding (1024 numbers) → stored in PostgreSQL
    │
    ▼
Job Published
    │
    ▼
Voyage AI → job embedding (1024 numbers) → stored in PostgreSQL
    │
    ▼
Run Ranking API
    │
    ├── pgvector cosine similarity → finds top N similar candidates (fast, cheap)
    │
    └── Claude Sonnet → scores each candidate 0-100 with rationale (accurate, costs credits)
            │
            ▼
    Blended score = 30% vector similarity + 70% Claude score
            │
            ▼
    Results saved to rankings table
```

---

## Flyway Migrations

| Version | Description |
|---|---|
| V1 | Core schema (organisations, users, jobs, candidates) |
| V2 | pgvector extension + indexes |
| V3 | Rankings, outreach drafts, AI audit log |
| V4 | Seed super admin user |
| V5 | Add sent_at to outreach drafts |
| V6 | Add source field to candidates |
| V7 | Add auto-process config to jobs |
| V8 | Fix embedding dimensions 1536 → 1024 |

---

## Useful URLs

| URL | Description |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Swagger API documentation |
| `http://localhost:8080/actuator/health` | Application health check |
| `http://localhost:9000` | MinIO storage (API) |
| `http://localhost:9001` | MinIO browser UI |

---

## Default Credentials (Dev)

| Service | Username | Password |
|---|---|---|
| PostgreSQL | `hireflow` | `hireflow` |
| MinIO | `minioadmin` | `minioadmin` |
| Redis | — | no password |
