ALTER TABLE job_requisitions DROP COLUMN IF EXISTS embedding;
ALTER TABLE job_requisitions ADD COLUMN embedding vector(1024);

ALTER TABLE candidates DROP COLUMN IF EXISTS embedding;
ALTER TABLE candidates ADD COLUMN embedding vector(1024);
