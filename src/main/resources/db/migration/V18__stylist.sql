CREATE TABLE stylist_conversations (
    id           uuid PRIMARY KEY,
    user_id      uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title        text,
    status       text NOT NULL,
    created_at   timestamp NOT NULL,
    updated_at   timestamp,
    completed_at timestamp
);

CREATE INDEX idx_stylist_conversations_user_id_updated_at_id
    ON stylist_conversations (user_id, updated_at DESC, id DESC);

CREATE INDEX idx_stylist_conversations_executing_updated_at
    ON stylist_conversations (updated_at) WHERE status = 'EXECUTING';

CREATE TABLE stylist_messages (
    id              uuid PRIMARY KEY,
    conversation_id uuid NOT NULL REFERENCES stylist_conversations (id) ON DELETE CASCADE,
    sequence        integer NOT NULL,
    role            text NOT NULL,
    content         text NOT NULL,
    proposal        jsonb,
    created_at      timestamp NOT NULL,
    updated_at      timestamp,
    CONSTRAINT uq_stylist_messages_conversation_sequence UNIQUE (conversation_id, sequence)
);

CREATE TABLE stylist_searches (
    id                  uuid PRIMARY KEY,
    conversation_id     uuid NOT NULL REFERENCES stylist_conversations (id) ON DELETE CASCADE,
    proposal_message_id uuid NOT NULL REFERENCES stylist_messages (id),
    position            integer NOT NULL,
    title               text NOT NULL,
    prompt              text NOT NULL,
    match_profile       boolean NOT NULL,
    status              text NOT NULL,
    ai_prompt_query_id  uuid UNIQUE REFERENCES ai_prompt_queries (id) ON DELETE SET NULL,
    CONSTRAINT uq_stylist_searches_conversation_position UNIQUE (conversation_id, position)
);
