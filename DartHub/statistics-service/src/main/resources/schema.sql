CREATE TABLE IF NOT EXISTS schema_version (
  version    INTEGER PRIMARY KEY,
  applied_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS player_statistics (
  player_id            INTEGER PRIMARY KEY,
  partite_giocate      INTEGER NOT NULL DEFAULT 0,
  vittorie             INTEGER NOT NULL DEFAULT 0,
  percentuale_vittorie REAL    NOT NULL DEFAULT 0,
  punti_totali         INTEGER NOT NULL DEFAULT 0,
  punteggio_medio      REAL    NOT NULL DEFAULT 0,
  migliore_tiro        INTEGER NOT NULL DEFAULT 0,
  updated_at           TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_player_stats_rank ON player_statistics(percentuale_vittorie DESC, punti_totali DESC);

CREATE TABLE IF NOT EXISTS team_statistics (
  team_id              INTEGER PRIMARY KEY,
  partite_giocate      INTEGER NOT NULL DEFAULT 0,
  vittorie             INTEGER NOT NULL DEFAULT 0,
  percentuale_vittorie REAL    NOT NULL DEFAULT 0,
  punti_totali         INTEGER NOT NULL DEFAULT 0,
  punteggio_medio      REAL    NOT NULL DEFAULT 0,
  updated_at           TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS locale_statistics (
  locale_id          INTEGER NOT NULL,
  dartboard_id       INTEGER NOT NULL,
  numero_partite     INTEGER NOT NULL DEFAULT 0,
  giocatori_distinti INTEGER NOT NULL DEFAULT 0,
  ultimo_utilizzo    TEXT,
  PRIMARY KEY (locale_id, dartboard_id)
);

CREATE TABLE IF NOT EXISTS locale_players (
  locale_id    INTEGER NOT NULL,
  dartboard_id INTEGER NOT NULL,
  player_id    INTEGER NOT NULL,
  PRIMARY KEY (locale_id, dartboard_id, player_id)
);

CREATE TABLE IF NOT EXISTS game_statistics (
  game_type_id                 INTEGER PRIMARY KEY,
  partite_totali               INTEGER NOT NULL DEFAULT 0,
  media_giocatori_per_partita  REAL    NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS global_statistics (
  id             INTEGER PRIMARY KEY CHECK (id = 1),
  utenti_totali  INTEGER NOT NULL DEFAULT 0,
  locali_totali  INTEGER NOT NULL DEFAULT 0,
  partite_totali INTEGER NOT NULL DEFAULT 0,
  tornei_totali  INTEGER NOT NULL DEFAULT 0,
  updated_at     TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS processed_events (
  event_id     TEXT PRIMARY KEY,
  processed_at TEXT NOT NULL
);
