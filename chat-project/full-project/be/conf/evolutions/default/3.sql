-- !Ups
CREATE TABLE user_settings (
    user_id            BIGINT PRIMARY KEY REFERENCES accounts(id),
    typing_indicators  BOOLEAN NOT NULL DEFAULT TRUE,
    show_online_status BOOLEAN NOT NULL DEFAULT TRUE,
    notifications      BOOLEAN NOT NULL DEFAULT TRUE,
    sound_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO user_settings (user_id)
SELECT id FROM accounts;

-- !Downs
DROP TABLE IF EXISTS user_settings;
