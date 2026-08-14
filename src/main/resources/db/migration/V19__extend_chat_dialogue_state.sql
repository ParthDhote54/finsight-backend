-- Extend chat_session_state with structured dialogue state for multi-turn conversational intelligence.
ALTER TABLE chat_session_state
    ADD COLUMN dialogue_state JSONB;
