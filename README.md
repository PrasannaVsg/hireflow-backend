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
  - `POST /api/v1/outreach/draft` — email drafting
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

> All endpoints that modify data require a valid JWT token in the `Authorization: Bearer <token>` header. Get a token from the Login API first.

---

### Authentication

Authentication APIs manage who can access the system. Every user (recruiter, admin) must log in to get a token before calling any other API.

---

#### Login
> **Why:** This is the entry point for all users. When a recruiter opens the HireFlow app, they call this API with their email and password. The response contains an `accessToken` (valid 15 min) used in all subsequent API calls, and a `refreshToken` (valid 7 days) to get a new access token without logging in again.

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

---

#### Refresh Token
> **Why:** The access token expires every 15 minutes for security. Instead of asking the user to log in again, the frontend silently calls this API with the refresh token to get a fresh access token. This keeps the user's session alive without interruption.

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
Response:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 900
}
```

---

#### Logout
> **Why:** Invalidates the user's refresh token so it can no longer be used to generate new access tokens. Important for security when a recruiter logs out or their account is compromised.

```
POST /api/v1/auth/logout
Authorization: Bearer <token>
```

---

### Users

User management APIs let admins control who has access to the HireFlow platform and what they can do.

---

#### Create User
> **Why:** When a new recruiter or hiring manager joins the team, the admin calls this API to create their account. The `role` field controls access — `ADMIN` can manage everything, `RECRUITER` can only manage jobs and candidates within their organization.

```
POST /api/v1/users
Authorization: Bearer <token>
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

---

#### List Users
> **Why:** Admins use this to see all team members who have access to the platform — useful for auditing who is using the system and managing the team.

```
GET /api/v1/users?page=0&size=20
Authorization: Bearer <token>
```
Response:
```json
{
  "content": [
    {
      "id": "user-uuid",
      "fullName": "Sam Recruiter",
      "email": "sam@hireflow.io",
      "role": "RECRUITER",
      "enabled": true
    }
  ],
  "totalElements": 5,
  "page": 0,
  "size": 20
}
```

---

#### Disable User
> **Why:** When a recruiter leaves the company, the admin disables their account instead of deleting it. This preserves all their historical activity (jobs created, candidates ranked) while preventing them from logging in.

```
PATCH /api/v1/users/{userId}/disable
Authorization: Bearer <token>
```

---

### Jobs

Job APIs manage the job requisitions (open positions). Jobs are the central entity — candidates apply to jobs, rankings are done per job, and outreach emails are written for a specific job.

---

#### Create Job
> **Why:** This is how a recruiter posts a new open position. The job description you write here is what Voyage AI converts into an embedding vector. The better and more detailed the description, the more accurately the system will match candidates. `shortlistSize` controls how many candidates Claude AI will evaluate during ranking (to control cost). `scoreThreshold` filters out candidates below a minimum score.

```
POST /api/v1/jobs
Authorization: Bearer <token>
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

---

#### Publish Job (triggers AI embedding)
> **Why:** Moving a job from `DRAFT` to `OPEN` is what activates it for recruiting. This is also the trigger that calls Voyage AI to convert the job description into a 1024-dimension embedding vector, which is stored in PostgreSQL. Without this step, ranking cannot work because there is no job vector to compare candidates against. Always publish the job before uploading resumes.

```
PATCH /api/v1/jobs/{jobId}/status?status=OPEN
Authorization: Bearer <token>
```
Response:
```json
{
  "id": "0a6527e4-1809-4cdc-8199-6959391eb42c",
  "status": "OPEN"
}
```

---

#### Get Job by ID
> **Why:** Used by the UI to display the full details of a specific job — title, description, status, configuration. Also useful for verifying that a job was created correctly before starting to upload resumes.

```
GET /api/v1/jobs/{jobId}
Authorization: Bearer <token>
```
Response:
```json
{
  "id": "0a6527e4-1809-4cdc-8199-6959391eb42c",
  "title": "Senior Java Engineer",
  "description": "We need a Java developer with Spring Boot...",
  "location": "Bangalore",
  "seniority": "Senior",
  "status": "OPEN",
  "autoProcessEnabled": false,
  "shortlistSize": 25,
  "scoreThreshold": 60.0,
  "createdAt": "2026-06-13T10:00:00Z"
}
```

---

#### Update Job
> **Why:** Allows a recruiter to correct or improve a job description after creation. For example, if the hiring manager changes the requirements, the recruiter updates the description here. **Important:** after updating, call the Reindex API to regenerate the embedding so rankings stay accurate with the new description.

```
PUT /api/v1/jobs/{jobId}
Authorization: Bearer <token>
Content-Type: application/json
```
Request:
```json
{
  "title": "Senior Java Engineer (Updated)",
  "description": "Updated job description with Kubernetes requirement added...",
  "location": "Remote",
  "seniority": "Senior",
  "requiredSkills": "Java, Spring Boot, Kubernetes",
  "shortlistSize": 30,
  "scoreThreshold": 65.0
}
```

---

#### Reindex Job Embedding
> **Why:** When a job description is updated, the old embedding stored in PostgreSQL is now stale — it reflects the old description. Calling this API tells Voyage AI to re-process the updated description and replaces the stored vector. Without reindexing, the ranking system will still compare candidates against the old job description, giving inaccurate results.

```
POST /api/v1/jobs/{jobId}/reindex
Authorization: Bearer <token>
```
Response:
```json
{
  "id": "0a6527e4-1809-4cdc-8199-6959391eb42c",
  "message": "Job reindexed successfully"
}
```

---

#### Delete Job
> **Why:** Removes a job requisition that was created by mistake or is no longer needed. This also removes associated rankings and auto-process config for that job.

```
DELETE /api/v1/jobs/{jobId}
Authorization: Bearer <token>
```

---

#### List Jobs
> **Why:** Shows all job requisitions for the organization — used by the recruiter's dashboard to display open positions. Supports pagination to handle organizations with many jobs. Use the `status` filter to show only `OPEN`, `DRAFT`, or `CLOSED` jobs.

```
GET /api/v1/jobs?page=0&size=20
Authorization: Bearer <token>
```
Response:
```json
{
  "content": [
    {
      "id": "0a6527e4-1809-4cdc-8199-6959391eb42c",
      "title": "Senior Java Engineer",
      "status": "OPEN",
      "location": "Bangalore",
      "createdAt": "2026-06-13T10:00:00Z"
    }
  ],
  "totalElements": 3,
  "page": 0,
  "size": 20
}
```

---

### Candidates

Candidate APIs manage job applicants. Candidates are linked to a specific job and move through a hiring pipeline from SOURCED → SCREENING → INTERVIEW → OFFER → HIRED/REJECTED.

---

#### Create Single Candidate (manual entry)
> **Why:** Used when a recruiter wants to add a single candidate manually — for example, a referral from an employee, or someone who applied via email and doesn't have a resume file. The `resumeText` field accepts plain text of the resume, which Voyage AI will embed so this candidate can participate in AI ranking just like uploaded resumes.

```
POST /api/v1/candidates
Authorization: Bearer <token>
Content-Type: application/json
```
Request:
```json
{
  "fullName": "Jane Doe",
  "email": "jane.doe@example.com",
  "phone": "+91-9876543210",
  "jobId": "0a6527e4-1809-4cdc-8199-6959391eb42c",
  "source": "REFERRAL",
  "resumeText": "Jane Doe is a Senior Java Developer with 7 years of experience in Spring Boot, PostgreSQL, and microservices. She has led teams of 5 engineers and delivered 3 production systems."
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

---

#### Get Candidate by ID
> **Why:** Used by the UI to show a candidate's full profile page — their contact details, resume text, pipeline stage, and which job they applied for. Also useful when debugging ranking results to verify what resume text was stored for a candidate.

```
GET /api/v1/candidates/{candidateId}
Authorization: Bearer <token>
```
Response:
```json
{
  "id": "candidate-uuid",
  "fullName": "Jane Doe",
  "email": "jane.doe@example.com",
  "phone": "+91-9876543210",
  "source": "REFERRAL",
  "status": "ACTIVE",
  "pipelineStage": "SOURCED",
  "resumeText": "Jane Doe is a Senior Java Developer...",
  "jobId": "0a6527e4-1809-4cdc-8199-6959391eb42c",
  "createdAt": "2026-06-13T10:00:00Z"
}
```

---

#### Get Resume Download URL
> **Why:** Resume files are stored securely in MinIO (private storage). You cannot access them with a direct URL — you need a time-limited pre-signed URL that expires after 1 hour. This API generates that URL so the UI can show a "Download Resume" button that works temporarily without exposing the storage bucket publicly.

```
GET /api/v1/candidates/{candidateId}/resume-url
Authorization: Bearer <token>
```
Response:
```json
{
  "url": "http://localhost:9000/hireflow-resumes/candidate-uuid/resume.pdf?X-Amz-Expires=3600&X-Amz-Signature=...",
  "expiresIn": 3600
}
```

---

#### Bulk Upload Resumes
> **Why:** This is the most commonly used candidate intake method. A recruiter receives 50 resumes from LinkedIn or Naukri, selects all files, and uploads them in one request. The system processes them asynchronously in the background — it extracts text from each PDF/Word file using Apache Tika, saves the candidate record, stores the resume file in MinIO, and calls Voyage AI to generate an embedding for each resume. The `source` field tracks where candidates came from for analytics. Returns immediately with a job tracking ID — use the status API to check progress.

```
POST /api/v1/candidates/batch-upload
Authorization: Bearer <token>
Content-Type: multipart/form-data
```
Form fields:
| Field | Type | Description |
|---|---|---|
| `files` | File(s) | Resume files (.pdf or .docx) — can select multiple |
| `jobId` | Text | The job ID these candidates are applying for |
| `source` | Text | Where the resumes came from: `LINKEDIN` / `NAUKRI` / `INDEED` / `INTERNSHALA` / `REFERRAL` / `DIRECT` / `OTHER` |

Response:
```json
{
  "jobId": "4983f7e7-e66a-4b58-a939-a9c74cc1b7b9",
  "status": "QUEUED",
  "total": 5
}
```

---

#### Check Upload Status
> **Why:** Since bulk upload processes resumes in the background (to handle large batches without timing out), this API lets the UI poll to check whether all resumes have been processed. Once `state` is `COMPLETED`, you know all candidates are saved and embedded, and ranking can begin. The `failed` count tells you if any resumes could not be read (corrupted files, scanned PDFs, etc.).

```
GET /api/v1/candidates/batch-upload/{jobId}/status
Authorization: Bearer <token>
```
Response:
```json
{
  "jobId": "4983f7e7-e66a-4b58-a939-a9c74cc1b7b9",
  "state": "COMPLETED",
  "total": 5,
  "succeeded": 4,
  "failed": 1
}
```

---

#### List Candidates for a Job
> **Why:** Shows all candidates who applied for a specific job. This powers the candidate list view in the UI where recruiters can see everyone in their pipeline. Use the `stage` filter to view only candidates in a specific pipeline stage — e.g., show only candidates currently in `INTERVIEW` to prepare for today's interviews.

```
GET /api/v1/candidates?jobId={jobId}&page=0&size=20
Authorization: Bearer <token>
```
Response:
```json
{
  "content": [
    {
      "id": "candidate-uuid",
      "fullName": "John Smith",
      "email": "john@example.com",
      "source": "LINKEDIN",
      "pipelineStage": "SOURCED",
      "status": "ACTIVE"
    }
  ],
  "totalElements": 50,
  "page": 0,
  "size": 20
}
```

---

#### Move Pipeline Stage
> **Why:** This is how the recruiter tracks where each candidate is in the hiring process. After reviewing rankings, the recruiter moves shortlisted candidates to `SCREENING`. After a phone screen, they move them to `INTERVIEW`. This is the core workflow action — every stage change is recorded and feeds into the analytics hiring funnel. The system supports: `SOURCED` → `SCREENING` → `INTERVIEW` → `OFFER` → `HIRED` / `REJECTED`.

```
PATCH /api/v1/candidates/{candidateId}/stage?stage=SCREENING
Authorization: Bearer <token>
```
Response:
```json
{
  "id": "candidate-uuid",
  "fullName": "John Smith",
  "pipelineStage": "SCREENING",
  "updatedAt": "2026-06-13T11:00:00Z"
}
```

---

### Rankings

Ranking APIs use AI to evaluate and score candidates for a job. This is the core intelligence feature of HireFlow — instead of a recruiter manually reading 50 resumes, the AI reads all of them and ranks them best-to-worst with reasoning.

---

#### Run Ranking (calls Claude AI)
> **Why:** This is the most powerful API in the system. When called, it: (1) loads the job's stored embedding from PostgreSQL, (2) uses pgvector cosine similarity to find the top N most similar candidates fast and cheaply, (3) sends each shortlisted candidate's resume + job description to Claude AI, which reads them like a human recruiter and assigns a score 0–100 with a written rationale and skill-by-skill breakdown. The final score is a blend of 30% vector similarity + 70% Claude score. This gives you a ranked list of "best fit" candidates in seconds instead of hours. **Note:** Each call to this API consumes Anthropic API credits. Use `shortlistSize` to control how many candidates Claude evaluates (fewer = lower cost).

```
POST /api/v1/jobs/{jobId}/rankings/run?shortlistSize=10
Authorization: Bearer <token>
```
Response:
```json
[
  {
    "candidateId": "candidate-uuid",
    "candidateName": "John Smith",
    "score": 78.5,
    "vectorSimilarity": 0.854321,
    "llmScore": 82,
    "rationale": "Strong match for Java and Spring Boot requirements. 6 years of relevant experience with PostgreSQL and microservices. Missing Kubernetes experience which is listed as required.",
    "skillBreakdown": "{\"Java\": 90, \"Spring Boot\": 85, \"PostgreSQL\": 80, \"Kubernetes\": 20}",
    "model": "claude-sonnet-4-6"
  },
  {
    "candidateId": "candidate-uuid-2",
    "candidateName": "Priya Sharma",
    "score": 65.2,
    "vectorSimilarity": 0.741200,
    "llmScore": 68,
    "rationale": "Good Java background but only 3 years of experience. No Spring Boot projects mentioned. Strong on PostgreSQL.",
    "skillBreakdown": "{\"Java\": 75, \"Spring Boot\": 40, \"PostgreSQL\": 85}",
    "model": "claude-sonnet-4-6"
  }
]
```

---

#### View Rankings (no AI call — reads from DB)
> **Why:** After running ranking once, the results are saved in the database. This API reads those saved results without calling Claude AI again — so it is fast and free. Recruiters use this to review yesterday's ranking results, sort by score, or share rankings with hiring managers. The UI ranking table is powered by this API.

```
GET /api/v1/jobs/{jobId}/rankings?page=0&size=20
Authorization: Bearer <token>
```
Response:
```json
{
  "content": [
    {
      "candidateId": "candidate-uuid",
      "candidateName": "John Smith",
      "score": 78.5,
      "llmScore": 82,
      "rationale": "Strong match for Java and Spring Boot...",
      "rankedAt": "2026-06-13T09:00:00Z"
    }
  ],
  "totalElements": 10,
  "page": 0,
  "size": 20
}
```

---

### Outreach Emails

Outreach APIs use Claude AI to generate personalized recruitment emails. Instead of a recruiter writing the same email 20 times with slightly different candidate names, the AI reads the job description and each candidate's resume and writes a unique, relevant email for each person.

---

#### Generate Outreach Email (calls Claude AI)
> **Why:** Recruiters spend hours writing individual outreach emails. This API automates that — Claude reads the job requirements and the candidate's actual resume, then writes a personalized email that references the candidate's specific skills and why they are a good fit for this role. The `tone` field lets you control style: `professional` for enterprise roles, `friendly` for startups, `casual` for internships. The email is saved as a DRAFT first — the recruiter can review and approve it before sending. **Note:** Each call consumes Anthropic API credits.

```
POST /api/v1/outreach/draft
Authorization: Bearer <token>
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
  "subject": "Exciting Senior Java Engineer Opportunity at HireFlow",
  "body": "Dear John,\n\nI came across your profile and was impressed by your 6 years of Spring Boot experience and your work on PostgreSQL-backed microservices at your current company.\n\nWe have an exciting Senior Java Engineer opening at HireFlow in Bangalore that I believe would be a great fit for your background...",
  "status": "DRAFT",
  "createdAt": "2026-06-13T10:00:00Z"
}
```

---

#### Update Outreach Status
> **Why:** Provides an approval workflow before emails are sent. A recruiter can review the AI-generated email draft, make edits if needed, then mark it `APPROVED` when ready to send. If the email is not appropriate, it can be `REJECTED` without sending. This prevents accidentally sending a poorly written email to a candidate. Statuses: `DRAFT` → `APPROVED` → `SENT` or `DRAFT` → `REJECTED`.

```
PATCH /api/v1/outreach/{draftId}/status?status=APPROVED
Authorization: Bearer <token>
```
Response:
```json
{
  "id": "draft-uuid",
  "status": "APPROVED",
  "updatedAt": "2026-06-13T10:03:00Z"
}
```

---

#### Send Email (requires SMTP config)
> **Why:** Actually delivers the outreach email to the candidate's inbox via SMTP. Only works if `MAIL_USERNAME` and `MAIL_PASSWORD` environment variables are configured. The email is sent from the address specified in `MAIL_FROM`. After sending, the draft status updates to `SENT` with a timestamp. The recruiter can use this timestamp to track when outreach was made for each candidate.

```
POST /api/v1/outreach/{draftId}/send
Authorization: Bearer <token>
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

Analytics APIs give recruiters and managers visibility into hiring activity and AI usage. These power the dashboard UI with metrics like how many jobs are open, where candidates are in the funnel, and how many AI credits have been used.

---

#### Dashboard (powers the UI prototype)
> **Why:** This is the single API that feeds the entire recruiter dashboard screen. It returns everything needed in one call: open job count, active candidate count, AI usage stats, the hiring funnel breakdown by stage, and recent AI activity. The `days` parameter controls the time window — `days=30` shows data from the last 30 days. Used by the dashboard UI to show hiring health at a glance.

```
GET /api/v1/analytics/dashboard?days=30
Authorization: Bearer <token>
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

---

#### Overview
> **Why:** A lightweight summary endpoint that returns just the total AI token consumption for the organization within a time window. Useful for management reports and for monitoring whether AI costs are within budget. Faster than the full dashboard when you only need the cost summary.

```
GET /api/v1/analytics/overview?days=30
Authorization: Bearer <token>
```
Response:
```json
{
  "organisationId": "org-uuid",
  "periodDays": 30,
  "totalAiTokens": 185000
}
```

---

#### AI Usage Breakdown
> **Why:** Breaks down AI token usage by operation type — how many times was Embedding called vs Ranking vs Outreach, and how many tokens each consumed. This helps teams understand where AI costs are coming from. For example, if `rankingCalls` is high, the team might increase `scoreThreshold` to reduce the number of candidates Claude evaluates. If `embeddingCalls` is high, resumes are being uploaded frequently.

```
GET /api/v1/analytics/ai-usage?days=30
Authorization: Bearer <token>
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

### Auto Process

The Auto Process API combines ranking + shortlisting + email generation + pipeline movement into a single automated workflow. Instead of calling 4–5 separate APIs, one call does everything.

---

#### Trigger Auto Process
> **Why:** This is the fully automated "hands-free" mode. When called, the system: (1) runs ranking via Claude AI for all uploaded candidates, (2) filters out candidates below the `scoreThreshold`, (3) generates a personalized outreach email for each shortlisted candidate via Claude AI, (4) sends those emails, and (5) automatically moves shortlisted candidates to `SCREENING` in the pipeline. This is ideal for high-volume recruiting where the recruiter wants to process a batch of resumes overnight. **Requires** `autoProcessEnabled: true` on the job, and **consumes significant Anthropic credits** since it calls Claude for ranking + outreach for every shortlisted candidate.

```
POST /api/v1/jobs/{jobId}/auto-process
Authorization: Bearer <token>
```
Response:
```json
{
  "jobId": "0a6527e4-1809-4cdc-8199-6959391eb42c",
  "shortlisted": 5,
  "emailsSent": 5,
  "movedToScreening": 5
}
```

---

#### Get Auto Process Config
> **Why:** Retrieves the current automation settings for a job — whether auto-processing is enabled, what score threshold is set, how many candidates to shortlist, and what email tone to use. Useful for displaying current settings in the UI before the recruiter decides to change them.

```
GET /api/v1/jobs/{jobId}/auto-process/config
Authorization: Bearer <token>
```
Response:
```json
{
  "jobId": "0a6527e4-1809-4cdc-8199-6959391eb42c",
  "enabled": true,
  "shortlistSize": 10,
  "scoreThreshold": 70.0,
  "emailTone": "professional"
}
```

---

#### Update Auto Process Config
> **Why:** Allows a recruiter to tune the automation settings without recreating the job. For example, after reviewing rankings, they might raise the `scoreThreshold` from 60 to 75 to be more selective, or reduce `shortlistSize` from 25 to 10 to lower Claude API costs. `emailTone` can be changed to `friendly` for junior roles or `formal` for executive positions.

```
PATCH /api/v1/jobs/{jobId}/auto-process/config
Authorization: Bearer <token>
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
Response:
```json
{
  "jobId": "0a6527e4-1809-4cdc-8199-6959391eb42c",
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
1. POST /auth/login                        → Get access token
2. POST /jobs                              → Create job
3. PATCH /jobs/{id}/status?status=OPEN     → Publish job (Voyage AI embeds job description)
4. POST /candidates/batch-upload           → Upload resumes (Voyage AI embeds each resume)
5. GET  /candidates/batch-upload/{id}/status → Wait for upload to complete
6. POST /jobs/{id}/rankings/run            → Claude AI ranks candidates (costs credits)
7. GET  /jobs/{id}/rankings                → View ranked results (free — reads from DB)
8. PATCH /candidates/{id}/stage            → Move shortlisted candidates to SCREENING
9. POST /outreach/draft                    → Claude AI drafts email for a candidate
10. PATCH /outreach/{id}/status?status=APPROVED → Approve the email
11. POST /outreach/{id}/send               → Send the email
```

### Auto Flow (one API call)

```
1. POST /auth/login                        → Get access token
2. POST /jobs (autoProcessEnabled: true)   → Create job with automation on
3. PATCH /jobs/{id}/status?status=OPEN     → Publish job
4. POST /candidates/batch-upload           → Upload resumes
5. GET  /candidates/batch-upload/{id}/status → Wait for completion
6. POST /jobs/{id}/auto-process            → Everything automated:
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
| `http://localhost:8080/swagger-ui.html` | Swagger API documentation (interactive) |
| `http://localhost:8080/actuator/health` | Application health check |
| `http://localhost:9000` | MinIO storage API |
| `http://localhost:9001` | MinIO browser UI (bucket management) |

---

## Default Credentials (Dev)

| Service | Username | Password |
|---|---|---|
| PostgreSQL | `hireflow` | `hireflow` |
| MinIO | `minioadmin` | `minioadmin` |
| Redis | — | no password |
| HireFlow App | `admin@hireflow.io` | `changeme` |
