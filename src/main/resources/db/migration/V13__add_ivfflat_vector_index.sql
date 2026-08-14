-- Migration V13: Create IVFFlat Index for Vector Search Optimization
CREATE INDEX IF NOT EXISTS idx_transaction_vectors_embedding_ivfflat
ON transaction_vectors
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

-- ARCHITECTURAL NOTICE:
-- IVFFlat is selected for memory efficiency during early scale.
-- HNSW should be considered when vector count exceeds ~100k or EXPLAIN ANALYZE shows IVFFlat recall degradation.