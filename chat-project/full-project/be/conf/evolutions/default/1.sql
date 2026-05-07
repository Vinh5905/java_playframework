-- !Ups
CREATE TABLE accounts (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    username   VARCHAR(100) NOT NULL UNIQUE,
    is_bot     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO accounts (name, username, is_bot) VALUES
    ('Alice Johnson',      'alice',     false),
    ('Bob Smith',          'bob',       false),
    ('Elmer Laverty',      'elmer',     false),
    ('Florencio Dorrance', 'florencio', false),
    ('Lavern Laboy',       'lavern',    false),
    ('Titus Kitamura',     'titus',     false),
    ('Geoffrey Mott',      'geoffrey',  false),
    ('Alfonzo Schuessler', 'alfonzo',   false),
    ('ChatGPT Bot',        'gpt_bot',   true);

CREATE TABLE app_state (
    key   VARCHAR(100) PRIMARY KEY,
    value VARCHAR(1000)
);
INSERT INTO app_state (key, value) VALUES ('current_account_id', '1');

-- !Downs
DROP TABLE IF EXISTS app_state;
DROP TABLE IF EXISTS accounts;
