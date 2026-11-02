-- @version 1
CREATE TABLE throws_new (
  id                  INTEGER PRIMARY KEY AUTOINCREMENT,
  match_id            INTEGER NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
  participant_id      INTEGER NOT NULL REFERENCES match_participants(id) ON DELETE CASCADE,
  tipo                TEXT NOT NULL DEFAULT 'TIRO',
  settore             TEXT,
  moltiplicatore      TEXT,
  payload_json        TEXT,
  team_id             INTEGER,
  punti               INTEGER NOT NULL DEFAULT 0,
  numero_turno        INTEGER NOT NULL DEFAULT 0,
  esito               TEXT NOT NULL DEFAULT 'VALIDO'
                        CHECK (esito IN ('VALIDO','BUST','VITTORIA','IGNORATO')),
  annullato           INTEGER NOT NULL DEFAULT 0,
  timestamp_sensore   TEXT NOT NULL,
  timestamp_ricezione TEXT NOT NULL,
  origine             TEXT NOT NULL CHECK (origine IN ('LIVE','OFFLINE')),
  dartboard_id        INTEGER NOT NULL,
  UNIQUE (dartboard_id, timestamp_sensore)
);

INSERT INTO throws_new(id, match_id, participant_id, tipo, settore, moltiplicatore, punti, numero_turno,
                        esito, annullato, timestamp_sensore, timestamp_ricezione, origine, dartboard_id)
SELECT id, match_id, participant_id, 'TIRO', settore, moltiplicatore, punti, numero_turno,
       esito, annullato, timestamp_sensore, timestamp_ricezione, origine, dartboard_id
FROM throws;

DROP TABLE throws;
ALTER TABLE throws_new RENAME TO throws;
CREATE INDEX IF NOT EXISTS idx_throws_match ON throws(match_id, timestamp_sensore);

-- @version 2
CREATE TABLE IF NOT EXISTS match_absences (
  match_id INTEGER PRIMARY KEY REFERENCES matches(id),
  winner_player_id INTEGER,
  type TEXT NOT NULL CHECK(type IN ('VITTORIA_A_TAVOLINO','DOPPIA_ASSENZA')),
  resolved_at TEXT NOT NULL
);

-- @version 3
CREATE TABLE IF NOT EXISTS sensor_tournament_matches (
  match_id INTEGER PRIMARY KEY REFERENCES matches(id) ON DELETE CASCADE,
  started INTEGER NOT NULL DEFAULT 0
);

INSERT OR IGNORE INTO sensor_tournament_matches(match_id, started)
SELECT id, 0 FROM matches WHERE local_uuid LIKE 'tournament:%' AND stato = 'CREATA';
