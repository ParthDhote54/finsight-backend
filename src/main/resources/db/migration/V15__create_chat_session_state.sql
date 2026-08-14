-- Lightweight session state for conversational follow-ups.
-- TTL-bound: application should periodically purge rows older than 24h.
-- NOT long-term memory — every session re-derives facts from the database.

CREATE TABLE chat_session_state (
    conversation_id     UUID PRIMARY KEY,
    user_id             UUID NOT NULL,
    last_tool_name      VARCHAR(100),
    last_tool_params    JSONB,           -- key parameters from the last tool execution
    last_user_message   TEXT,
    last_answer_summary TEXT,
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_session_state_user ON chat_session_state(user_id);
CREATE INDEX idx_session_state_ttl ON chat_session_state(updated_at);
