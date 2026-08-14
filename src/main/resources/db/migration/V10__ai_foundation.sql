CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE outbox_events(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    last_attempt TIMESTAMP,
    next_attempt TIMESTAMP NOT NULL DEFAULT now(),
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    processed_at TIMESTAMP

);

--Ensure we never insert the same event twice.
CREATE UNIQUE INDEX idx_outbox_event_id ON outbox_events(event_id);


--Optimize the poller query : select ...where status = 'Pending'
CREATE INDEX idx_outbox_status_next_attempt ON outbox_events(status, next_attempt) WHERE status = 'PENDING';


CREATE TABLE transaction_vectors(
    transaction_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    embedding VECTOR(768),
    content_hash VARCHAR(64) NOT NULL,
    embedding_model VARCHAR(50) NOT NULL,
    embedding_version INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT fk_vector_transaction FOREIGN KEY(transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
    );


   --optimize similarity search, strongly isolated by tenant(user_id)
CREATE INDEX idx_transaction_vector_user_embedding
ON transaction_vectors USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)
WHERE user_id is NOT NULL;



--AI RESPONSE logging table(The Audit Trial)

CREATE TABLE ai_response_logs(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trace_id UUID NOT NULL,
    user_id UUID NOT NULL,
    endpoint VARCHAR(100) NOT NULL,
    prompt_version VARCHAR(20) NOT NULL,
    prompt TEXT NOT NULL,
    retrieved_chunks JSONB,
    response TEXT NOT NULL,
    input_tokens INT,
    output_tokens INT,
    latency_ms INT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
    );