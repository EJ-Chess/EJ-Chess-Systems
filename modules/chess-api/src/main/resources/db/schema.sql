-- EJa Chess — Game persistence schema
-- Supports H2 (dev/test) and PostgreSQL (prod)

CREATE TABLE IF NOT EXISTS games (
    id           VARCHAR(36)  PRIMARY KEY,
    pgn          TEXT         NOT NULL DEFAULT '',
    player_color VARCHAR(5)   NOT NULL DEFAULT 'WHITE',
    bot_color    VARCHAR(5)   DEFAULT NULL,
    bot_elo      INTEGER      DEFAULT NULL
);
