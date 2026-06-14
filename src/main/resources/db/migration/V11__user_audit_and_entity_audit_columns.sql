-- Level 1: add created_by_user_id / updated_by_user_id to ALL entity tables that extend BaseEntity
ALTER TABLE job_requisitions
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS updated_by_user_id UUID;

ALTER TABLE candidates
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS updated_by_user_id UUID;

ALTER TABLE rankings
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS updated_by_user_id UUID;

ALTER TABLE outreach_drafts
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS updated_by_user_id UUID;

ALTER TABLE ai_audit_log
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS updated_by_user_id UUID;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS updated_by_user_id UUID;

ALTER TABLE organisations
    ADD COLUMN IF NOT EXISTS created_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS updated_by_user_id UUID;

-- Level 2: user action audit log
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
