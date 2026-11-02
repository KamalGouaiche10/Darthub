CREATE TABLE IF NOT EXISTS schema_version (
  version    INTEGER PRIMARY KEY,
  applied_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tournaments (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  nome         TEXT NOT NULL,
  game_type_id INTEGER NOT NULL,
  modalita     TEXT NOT NULL CHECK (modalita IN ('INDIVIDUALE','SQUADRA')),
  formula      TEXT NOT NULL DEFAULT 'GIRONE'
                 CHECK (formula IN ('GIRONE','ELIMINAZIONE')),
  punti_vittoria INTEGER NOT NULL DEFAULT 3,
  punti_pareggio INTEGER NOT NULL DEFAULT 1,
  punti_sconfitta INTEGER NOT NULL DEFAULT 0,
  data_inizio  TEXT NOT NULL,
  data_fine    TEXT,
  stato        TEXT NOT NULL DEFAULT 'APERTO'
                 CHECK (stato IN ('APERTO','IN_CORSO','CHIUSO')),
  vincitore_participant_id INTEGER
);

CREATE TABLE IF NOT EXISTS tournament_locali (
  tournament_id INTEGER NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
  locale_id     INTEGER NOT NULL,
  PRIMARY KEY (tournament_id, locale_id)
);

CREATE TABLE IF NOT EXISTS enrollments (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  tournament_id  INTEGER NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
  participant_id INTEGER NOT NULL,
  tipo           TEXT NOT NULL CHECK (tipo IN ('GIOCATORE','SQUADRA')),
  data_iscrizione TEXT NOT NULL,
  UNIQUE (tournament_id, participant_id, tipo)
);

CREATE TABLE IF NOT EXISTS tournament_matches (
  tournament_id INTEGER NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
  match_id      INTEGER NOT NULL,
  turno         INTEGER NOT NULL DEFAULT 1,
  slot          INTEGER,
  PRIMARY KEY (tournament_id, match_id)
);

CREATE TABLE IF NOT EXISTS bracket_slots (
  tournament_id  INTEGER NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
  turno          INTEGER NOT NULL,
  slot           INTEGER NOT NULL,
  participant_id INTEGER,
  match_id       INTEGER,
  PRIMARY KEY (tournament_id, turno, slot)
);

CREATE TABLE IF NOT EXISTS standings (
  tournament_id   INTEGER NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
  participant_id  INTEGER NOT NULL,
  punti           INTEGER NOT NULL DEFAULT 0,
  partite_giocate INTEGER NOT NULL DEFAULT 0,
  vittorie        INTEGER NOT NULL DEFAULT 0,
  posizione       INTEGER,
  PRIMARY KEY (tournament_id, participant_id)
);
CREATE INDEX IF NOT EXISTS idx_standings_ordine ON standings(tournament_id, punti DESC);

CREATE TABLE IF NOT EXISTS processed_events (
  event_id     TEXT PRIMARY KEY,
  processed_at TEXT NOT NULL
);
