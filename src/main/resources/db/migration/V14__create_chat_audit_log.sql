-- Chat audit log for Phase 4 AI tool-calling observability.
-- Stores one row per chat turn with full tool execution trace,
-- hallucination flags, token counts, and citation metadata.

CREATE TABLE chat_audit_log (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL,
    conversation_id         UUID NOT NULL,
    user_message            TEXT NOT NULL,
    final_answer            TEXT,
    tool_calls              JSONB,          -- ordered array of tool invocations with args + results
    retrieved_transaction_ids UUID[],        -- IDs returned by vector search
    cited_transaction_ids   UUID[],         -- IDs referenced in the final answer
    prompt_tokens           INT,
    completion_tokens       INT,
    total_tokens            INT,
    flagged_hallucination   BOOLEAN NOT NULL DEFAULT FALSE,
    hallucination_details   TEXT,           -- which number or citation failed validation
    merchant_tier_used      INT,            -- 1=registry, 2=normalized, 3=semantic
    tool_turns              INT NOT NULL DEFAULT 0,
    created_at              TIMESTAMP NOT NULL DEFAULT now()
);

-- Index for querying by user + conversation (audit trail lookups)
CREATE INDEX idx_chat_audit_user_conv ON chat_audit_log(user_id, conversation_id);

-- Index for monitoring hallucination rates
CREATE INDEX idx_chat_audit_hallucination ON chat_audit_log(flagged_hallucination) WHERE flagged_hallucination = TRUE;
