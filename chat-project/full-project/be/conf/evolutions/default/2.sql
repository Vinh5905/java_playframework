-- !Ups
CREATE TABLE conversations (
    id           BIGSERIAL PRIMARY KEY,
    participant1 BIGINT NOT NULL REFERENCES accounts(id),
    participant2 BIGINT NOT NULL REFERENCES accounts(id),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX unique_conversation_pair
ON conversations (LEAST(participant1, participant2), GREATEST(participant1, participant2));

INSERT INTO conversations (participant1, participant2) VALUES
    (1, 3),
    (1, 4),
    (1, 5),
    (1, 6),
    (1, 7),
    (1, 8),
    (1, 9);

-- !Downs
DROP TABLE IF EXISTS conversations;
