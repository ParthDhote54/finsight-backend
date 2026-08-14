-- A conversation UUID is client-supplied and is not globally tenant-unique.
-- Include the owning user in the row identity so the same conversation UUID
-- cannot cause another tenant's session state to be merged or overwritten.

ALTER TABLE chat_session_state
    DROP CONSTRAINT chat_session_state_pkey;

ALTER TABLE chat_session_state
    ADD CONSTRAINT chat_session_state_pkey
        PRIMARY KEY (user_id, conversation_id);
