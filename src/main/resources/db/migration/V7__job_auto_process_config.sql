ALTER TABLE job_requisitions ADD COLUMN IF NOT EXISTS auto_process_enabled  BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE job_requisitions ADD COLUMN IF NOT EXISTS auto_shortlist_size   INTEGER      NOT NULL DEFAULT 25;
ALTER TABLE job_requisitions ADD COLUMN IF NOT EXISTS auto_score_threshold  NUMERIC(5,2) NOT NULL DEFAULT 60.0;
ALTER TABLE job_requisitions ADD COLUMN IF NOT EXISTS auto_email_tone       VARCHAR(40)  NOT NULL DEFAULT 'professional';
