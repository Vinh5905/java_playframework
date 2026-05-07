-- !Ups
CREATE TABLE conversations (
    id           BIGSERIAL PRIMARY KEY,
    participant1 BIGINT NOT NULL REFERENCES accounts(id),
    participant2 BIGINT NOT NULL REFERENCES accounts(id),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_conversation
        UNIQUE(LEAST(participant1, participant2), GREATEST(participant1, participant2))
);

-- !Downs
DROP TABLE IF EXISTS conversations;
