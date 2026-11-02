CREATE TABLE IF NOT EXISTS schema_version (
  version    INTEGER PRIMARY KEY,
  applied_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS local_matches (
  local_uuid    TEXT PRIMARY KEY,
  server_id     INTEGER UNIQUE,
  dartboard_id  INTEGER NOT NULL,
  scoring_rule  TEXT NOT NULL,
  origine       TEXT NOT NULL CHECK (origine IN ('SERVER','LOCALE')),
  stato         TEXT NOT NULL,
  importata     INTEGER NOT NULL DEFAULT 0,
  data_inizio   TEXT NOT NULL,
  data_fine     TEXT
);

CREATE TABLE IF NOT EXISTS local_participants (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  local_uuid            TEXT NOT NULL REFERENCES local_matches(local_uuid) ON DELETE CASCADE,
  server_participant_id INTEGER,
  player_id             INTEGER,
  nome_visualizzato     TEXT NOT NULL,
  ordine_turno          INTEGER NOT NULL,
  punteggio_corrente    INTEGER NOT NULL,
  UNIQUE (local_uuid, ordine_turno)
);

CREATE TABLE IF NOT EXISTS buffered_throws (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  local_uuid            TEXT NOT NULL REFERENCES local_matches(local_uuid) ON DELETE CASCADE,
  local_participant_id  INTEGER NOT NULL REFERENCES local_participants(id) ON DELETE CASCADE,
  settore               TEXT NOT NULL,
  moltiplicatore        TEXT NOT NULL,
  punti                 INTEGER NOT NULL,
  numero_turno          INTEGER NOT NULL,
  esito                 TEXT NOT NULL,
  annullato             INTEGER NOT NULL DEFAULT 0,
  timestamp_sensore     TEXT NOT NULL UNIQUE,
  sincronizzato         INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_buffer_pendenti ON buffered_throws(sincronizzato, timestamp_sensore);

CREATE TABLE IF NOT EXISTS rule_cache (
  id                 INTEGER PRIMARY KEY CHECK (id = 1),
  game_type_id       INTEGER,
  scoring_rule_json  TEXT,
  roster_json        TEXT,
  updated_at         TEXT NOT NULL
);
