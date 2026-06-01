-- Tournament Service Database Schema
-- Compatible with H2 and PostgreSQL
-- Note: Slick auto-creates all tables from Tables.scala via createIfNotExists

CREATE TABLE IF NOT EXISTS tournaments (
  id              VARCHAR(36)  PRIMARY KEY,
  name            VARCHAR(255) NOT NULL,
  status          VARCHAR(10)  NOT NULL DEFAULT 'created',
  nb_rounds       INTEGER      NOT NULL,
  current_round   INTEGER      NOT NULL DEFAULT 0,
  clock_limit     INTEGER      NOT NULL DEFAULT 300,
  clock_increment INTEGER      NOT NULL DEFAULT 3,
  rated           BOOLEAN      NOT NULL DEFAULT TRUE,
  created_by      VARCHAR(100) NOT NULL,
  starts_at       VARCHAR(30)
);

CREATE TABLE IF NOT EXISTS tournament_players (
  tournament_id VARCHAR(36)  NOT NULL,
  bot_id        VARCHAR(100) NOT NULL,
  bot_name      VARCHAR(100) NOT NULL DEFAULT '',
  points        DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  tie_break     DOUBLE PRECISION NOT NULL DEFAULT 0.0,
  wins          INTEGER NOT NULL DEFAULT 0,
  draws         INTEGER NOT NULL DEFAULT 0,
  losses        INTEGER NOT NULL DEFAULT 0,
  nb_games      INTEGER NOT NULL DEFAULT 0,
  color_balance INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (tournament_id, bot_id)
);

CREATE TABLE IF NOT EXISTS pairings (
  id            VARCHAR(36)  PRIMARY KEY,
  tournament_id VARCHAR(36)  NOT NULL,
  round         INTEGER      NOT NULL,
  white_id      VARCHAR(100) NOT NULL,
  white_name    VARCHAR(100) NOT NULL DEFAULT '',
  black_id      VARCHAR(100) NOT NULL,
  black_name    VARCHAR(100) NOT NULL DEFAULT '',
  game_id       VARCHAR(36),
  winner        VARCHAR(5)
);
