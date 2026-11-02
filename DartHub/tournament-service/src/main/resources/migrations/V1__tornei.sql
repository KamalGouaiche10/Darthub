-- @version 1
ALTER TABLE tournaments ADD COLUMN data_fine_programmata TEXT;
ALTER TABLE tournaments ADD COLUMN data_inizio_effettiva TEXT;
ALTER TABLE tournaments ADD COLUMN data_fine_effettiva TEXT;

UPDATE tournaments SET data_fine_programmata = COALESCE(data_fine, data_inizio),
  data_inizio_effettiva = CASE WHEN stato <> 'APERTO' THEN data_inizio END,
  data_fine_effettiva = CASE WHEN stato = 'CHIUSO' THEN data_fine END;

-- @version 2
ALTER TABLE tournament_matches ADD COLUMN home_participant_id INTEGER;
ALTER TABLE tournament_matches ADD COLUMN away_participant_id INTEGER;
ALTER TABLE tournament_matches ADD COLUMN locale_id INTEGER;

CREATE TABLE IF NOT EXISTS pairing_notice_reads (
  user_id INTEGER NOT NULL,
  match_id INTEGER NOT NULL,
  read_at TEXT NOT NULL,
  PRIMARY KEY (user_id, match_id)
);

-- @version 3
CREATE TABLE IF NOT EXISTS match_appointments (
  match_id INTEGER PRIMARY KEY,
  tournament_id INTEGER NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
  dartboard_id INTEGER NOT NULL,
  scheduled_at TEXT NOT NULL,
  duration_minutes INTEGER NOT NULL DEFAULT 60,
  home_ready_at TEXT,
  away_ready_at TEXT,
  outcome TEXT,
  revision INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_match_appointments_due ON match_appointments(scheduled_at, outcome);
CREATE TABLE IF NOT EXISTS processed_matches (
  match_id INTEGER PRIMARY KEY,
  processed_at TEXT NOT NULL
);

-- @version 4
CREATE TABLE IF NOT EXISTS match_acceptances (
  match_id INTEGER PRIMARY KEY,
  home_accepted_at TEXT,
  away_accepted_at TEXT
);
