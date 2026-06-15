-- ── rankings ───────────────────────────────────────────────────────────────
CREATE TABLE rankings (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id            UUID         NOT NULL REFERENCES job_requisitions(id),
    candidate_id      UUID         NOT NULL REFERENCES candidates(id),
    score             NUMERIC(7,4) NOT NULL,
    vector_similarity NUMERIC(9,8),
    llm_score         INTEGER,
    rationale         TEXT,
    skill_breakdown   JSONB,
    model             VARCHAR(60),
    claude_error      TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version           BIGINT       NOT NULL DEFAULT 0,
    created_by_user_id UUID,
    updated_by_user_id UUID,
    CONSTRAINT uk_rank_job_cand UNIQUE (job_id, candidate_id)
);
CREATE INDEX idx_rank_job   ON rankings(job_id);
CREATE INDEX idx_rank_score ON rankings(score);

-- ── outreach_drafts ────────────────────────────────────────────────────────
CREATE TABLE outreach_drafts (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id       UUID         NOT NULL REFERENCES candidates(id),
    job_id             UUID         NOT NULL REFERENCES job_requisitions(id),
    created_by         UUID         NOT NULL REFERENCES users(id),
    subject            VARCHAR(250) NOT NULL,
    body               TEXT         NOT NULL,
    channel            VARCHAR(20)  NOT NULL DEFAULT 'EMAIL',
    status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    model              VARCHAR(60),
    sent_at            TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0,
    created_by_user_id UUID,
    updated_by_user_id UUID
);
CREATE INDEX idx_outreach_cand   ON outreach_drafts(candidate_id);
CREATE INDEX idx_outreach_status ON outreach_drafts(status);

-- ── ai_audit_log ───────────────────────────────────────────────────────────
CREATE TABLE ai_audit_log (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id  UUID        NOT NULL,
    actor_user_id    UUID,
    operation        VARCHAR(20) NOT NULL,
    model            VARCHAR(60),
    request_tokens   INTEGER,
    response_tokens  INTEGER,
    latency_ms       BIGINT,
    success          BOOLEAN     NOT NULL DEFAULT TRUE,
    error_message    TEXT,
    prompt_hash      VARCHAR(64),
    target_entity_id UUID,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT      NOT NULL DEFAULT 0,
    created_by_user_id UUID,
    updated_by_user_id UUID
);
CREATE INDEX idx_audit_org     ON ai_audit_log(organisation_id);
CREATE INDEX idx_audit_op      ON ai_audit_log(operation);
CREATE INDEX idx_audit_created ON ai_audit_log(created_at);

-- ── user_audit_log ─────────────────────────────────────────────────────────
CREATE TABLE user_audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organisation_id UUID        NOT NULL,
    user_id         UUID,
    user_name       VARCHAR(120),
    action          VARCHAR(50) NOT NULL,
    entity_type     VARCHAR(30),
    entity_id       UUID,
    entity_name     VARCHAR(200),
    details         TEXT,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ual_org     ON user_audit_log(organisation_id);
CREATE INDEX idx_ual_user    ON user_audit_log(user_id);
CREATE INDEX idx_ual_created ON user_audit_log(created_at DESC);
CREATE INDEX idx_ual_action  ON user_audit_log(action);
