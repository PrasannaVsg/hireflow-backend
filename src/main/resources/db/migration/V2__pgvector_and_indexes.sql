-- Enable pgvector and add embedding columns + HNSW indexes for cosine similarity.
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE job_requisitions ADD COLUMN embedding vector(1536);
ALTER TABLE candidates       ADD COLUMN embedding vector(1536);

-- HNSW index for fast approximate nearest-neighbour search using cosine distance.
CREATE INDEX idx_cand_embedding_hnsw
    ON candidates USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX idx_job_embedding_hnsw
    ON job_requisitions USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
