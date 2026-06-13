-- Organisations, users, jobs, candidates (vectors added in V2).
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE organisations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name             VARCHAR(200) NOT NULL,
    slug             VARCHAR(80)  NOT NULL,
    plan_tier        VARCHAR(40)  NOT NULL DEFAULT 'STANDARD',
    monthly_ai_quota INTEGER      NOT NULL DEFAULT 5000,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_org_slug UNIQUE (slug)
);

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    role            VARCHAR(30)  NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    token_version   INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_email UNIQUE (email),
    CONSTRAINT chk_user_role CHECK (role IN
        ('SUPER_ADMIN','ORG_ADMIN','RECRUITER','HIRING_MANAGER','READ_ONLY'))
);
CREATE INDEX idx_user_org ON users(organisation_id);

CREATE TABLE job_requisitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID NOT NULL REFERENCES organisations(id),
    created_by      UUID NOT NULL REFERENCES users(id),
    title           VARCHAR(200) NOT NULL,
    description     TEXT NOT NULL,
    location        VARCHAR(120),
    seniority       VARCHAR(60),
    required_skills TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_job_org    ON job_requisitions(organisation_id);
CREATE INDEX idx_job_status ON job_requisitions(status);

CREATE TABLE candidates (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id   UUID NOT NULL REFERENCES organisations(id),
    job_id            UUID REFERENCES job_requisitions(id),
    full_name         VARCHAR(160) NOT NULL,
    email             VARCHAR(255),
    phone             VARCHAR(40),
    resume_object_key VARCHAR(512),
    resume_text       TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'NEW',
    pipeline_stage    VARCHAR(20) NOT NULL DEFAULT 'SOURCED',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_cand_org   ON candidates(organisation_id);
CREATE INDEX idx_cand_job   ON candidates(job_id);
CREATE INDEX idx_cand_stage ON candidates(pipeline_stage);
