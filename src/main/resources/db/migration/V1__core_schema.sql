CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS vector;

-- ── organisations ──────────────────────────────────────────────────────────
CREATE TABLE organisations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    slug                VARCHAR(80)  NOT NULL,
    plan_tier           VARCHAR(40)  NOT NULL DEFAULT 'STANDARD',
    monthly_ai_quota    INTEGER      NOT NULL DEFAULT 5000,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0,
    created_by_user_id  UUID,
    updated_by_user_id  UUID,
    mail_from           VARCHAR(200),
    mail_reply_to       VARCHAR(200),
    CONSTRAINT uk_org_slug UNIQUE (slug)
);

-- ── users ──────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id      UUID         NOT NULL REFERENCES organisations(id),
    email                VARCHAR(255) NOT NULL,
    password_hash        VARCHAR(100) NOT NULL,
    full_name            VARCHAR(120) NOT NULL,
    role                 VARCHAR(30)  NOT NULL,
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at        TIMESTAMPTZ,
    token_version        INTEGER      NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version              BIGINT       NOT NULL DEFAULT 0,
    created_by_user_id   UUID,
    updated_by_user_id   UUID,
    CONSTRAINT uk_user_email UNIQUE (email),
    CONSTRAINT chk_user_role CHECK (role IN
        ('SUPER_ADMIN','ORG_ADMIN','RECRUITER','HIRING_MANAGER','READ_ONLY'))
);
CREATE INDEX idx_user_org ON users(organisation_id);

-- ── job_requisitions ───────────────────────────────────────────────────────
CREATE TABLE job_requisitions (
    id                           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id              UUID         NOT NULL REFERENCES organisations(id),
    created_by                   UUID         NOT NULL REFERENCES users(id),
    job_code                     VARCHAR(40),
    title                        VARCHAR(200) NOT NULL,
    client_name                  VARCHAR(200),
    description                  TEXT         NOT NULL,
    locations                    TEXT,
    seniority                    VARCHAR(60),
    exp_min                      INTEGER,
    exp_max                      INTEGER,
    required_skills              TEXT,
    budget_min                   NUMERIC(12,2),
    budget_max                   NUMERIC(12,2),
    mail_template                TEXT,
    status                       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    auto_process_enabled         BOOLEAN      NOT NULL DEFAULT FALSE,
    auto_email_on_stage_change   BOOLEAN      NOT NULL DEFAULT FALSE,
    auto_shortlist_size          INTEGER      NOT NULL DEFAULT 25,
    auto_score_threshold         NUMERIC(5,2) NOT NULL DEFAULT 60.0,
    auto_email_tone              VARCHAR(40)  NOT NULL DEFAULT 'professional',
    embedding                    vector(1024),
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                      BIGINT       NOT NULL DEFAULT 0,
    created_by_user_id           UUID,
    updated_by_user_id           UUID
);
CREATE INDEX idx_job_org    ON job_requisitions(organisation_id);
CREATE INDEX idx_job_status ON job_requisitions(status);
CREATE INDEX idx_job_embedding_hnsw
    ON job_requisitions USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- ── candidates ─────────────────────────────────────────────────────────────
CREATE TABLE candidates (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id    UUID         NOT NULL REFERENCES organisations(id),
    job_id             UUID         REFERENCES job_requisitions(id),
    full_name          VARCHAR(160) NOT NULL,
    email              VARCHAR(255),
    phone              VARCHAR(40),
    source             VARCHAR(20),
    resume_object_key  VARCHAR(512),
    resume_text        TEXT,
    status             VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    pipeline_stage     VARCHAR(30)  NOT NULL DEFAULT 'SOURCED',
    offer_amount       NUMERIC(12,2),
    rejection_reason   VARCHAR(500),
    embedding          vector(1024),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0,
    created_by_user_id UUID,
    updated_by_user_id UUID
);
CREATE INDEX idx_cand_org   ON candidates(organisation_id);
CREATE INDEX idx_cand_job   ON candidates(job_id);
CREATE INDEX idx_cand_stage ON candidates(pipeline_stage);
CREATE INDEX idx_cand_email ON candidates(email);
CREATE INDEX idx_cand_embedding_hnsw
    ON candidates USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
