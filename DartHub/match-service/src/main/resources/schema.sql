CREATE TABLE IF NOT EXISTS schema_version (
  version    INTEGER PRIMARY KEY,
  applied_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS teams (
  id   INTEGER PRIMARY KEY AUTOINCREMENT,
  nome TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS team_members (
  team_id   INTEGER NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
  player_id INTEGER NOT NULL,
  PRIMARY KEY (team_id, player_id)
);

CREATE TABLE IF NOT EXISTS matches (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  dartboard_id    INTEGER NOT NULL,
  locale_id       INTEGER NOT NULL,
  game_type_id    INTEGER NOT NULL,
  scoring_rule_id INTEGER NOT NULL,
  modalita        TEXT NOT NULL CHECK (modalita IN ('INDIVIDUALE','SQUADRA')),
  stato           TEXT NOT NULL DEFAULT 'CREATA'
                    CHECK (stato IN ('CREATA','IN_CORSO','COMPLETATA','SINCRONIZZATA','ANNULLATA')),
  created_by      INTEGER NOT NULL,
  local_uuid      TEXT UNIQUE,
  clock_offset_ms INTEGER NOT NULL DEFAULT 0,
  data_inizio     TEXT,
  data_fine       TEXT
);
CREATE INDEX IF NOT EXISTS idx_matches_dartboard ON matches(dartboard_id);
CREATE INDEX IF NOT EXISTS idx_matches_locale_stato ON matches(locale_id, stato);

CREATE TABLE IF NOT EXISTS sensor_tournament_matches (
  match_id INTEGER PRIMARY KEY REFERENCES matches(id) ON DELETE CASCADE,
  started INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS match_participants (
  id                  INTEGER PRIMARY KEY AUTOINCREMENT,
  match_id            INTEGER NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
  player_id           INTEGER,
  team_id             INTEGER REFERENCES teams(id),
  ordine_turno        INTEGER NOT NULL,
  punteggio_corrente  INTEGER NOT NULL DEFAULT 0,
  ritirato            INTEGER NOT NULL DEFAULT 0,
  UNIQUE (match_id, ordine_turno),
  CHECK ((player_id IS NOT NULL AND team_id IS NULL)
      OR (player_id IS NULL AND team_id IS NOT NULL)
      OR (player_id IS NULL AND team_id IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_participants_match  ON match_participants(match_id);
CREATE INDEX IF NOT EXISTS idx_participants_player ON match_participants(player_id);

CREATE TABLE IF NOT EXISTS throws (
  id                  INTEGER PRIMARY KEY AUTOINCREMENT,
  match_id            INTEGER NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
  participant_id      INTEGER NOT NULL REFERENCES match_participants(id) ON DELETE CASCADE,
  settore             TEXT NOT NULL,
  moltiplicatore      TEXT NOT NULL,
  punti               INTEGER NOT NULL,
  numero_turno        INTEGER NOT NULL,
  esito               TEXT NOT NULL DEFAULT 'VALIDO'
                        CHECK (esito IN ('VALIDO','BUST','VITTORIA','IGNORATO')),
  annullato           INTEGER NOT NULL DEFAULT 0,
  timestamp_sensore   TEXT NOT NULL,
  timestamp_ricezione TEXT NOT NULL,
  origine             TEXT NOT NULL CHECK (origine IN ('LIVE','OFFLINE')),
  dartboard_id        INTEGER NOT NULL,
  UNIQUE (dartboard_id, timestamp_sensore)
);
CREATE INDEX IF NOT EXISTS idx_throws_match ON throws(match_id, timestamp_sensore);

CREATE TABLE IF NOT EXISTS match_results (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  match_id         INTEGER NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
  participant_id   INTEGER NOT NULL REFERENCES match_participants(id) ON DELETE CASCADE,
  punteggio_finale INTEGER NOT NULL,
  posizione        INTEGER NOT NULL,
  vincitore        INTEGER NOT NULL DEFAULT 0,
  UNIQUE (match_id, participant_id)
);
