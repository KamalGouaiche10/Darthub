CREATE TABLE IF NOT EXISTS schema_version (
  version    INTEGER PRIMARY KEY,
  applied_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS game_types (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  nome        TEXT NOT NULL UNIQUE,
  descrizione TEXT
);

CREATE TABLE IF NOT EXISTS sensor_types (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  game_type_id INTEGER NOT NULL REFERENCES game_types(id) ON DELETE CASCADE,
  nome         TEXT NOT NULL,
  formato_dato TEXT NOT NULL,
  modalita     TEXT NOT NULL CHECK (modalita IN ('REALE','EMULATO')),
  UNIQUE (game_type_id, nome)
);

CREATE TABLE IF NOT EXISTS scoring_rules (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  game_type_id          INTEGER NOT NULL REFERENCES game_types(id) ON DELETE CASCADE,
  variante              TEXT NOT NULL,
  direzione             TEXT NOT NULL DEFAULT 'DECRESCENTE'
                          CHECK (direzione IN ('DECRESCENTE','CRESCENTE')),
  punteggio_iniziale    INTEGER NOT NULL,
  punteggio_obiettivo   INTEGER NOT NULL DEFAULT 0,
  chiusura_double       INTEGER NOT NULL DEFAULT 0,
  bust_annulla_turno    INTEGER NOT NULL DEFAULT 1,
  max_tiri_per_turno    INTEGER NOT NULL DEFAULT 3,
  UNIQUE (game_type_id, variante)
);
