CREATE TABLE IF NOT EXISTS schema_version (
  version    INTEGER PRIMARY KEY,
  applied_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS locali (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  nome          TEXT NOT NULL,
  indirizzo     TEXT,
  tipo          TEXT NOT NULL CHECK (tipo IN ('PUBBLICO','PRIVATO')),
  owner_user_id INTEGER NOT NULL,
  created_at    TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_locali_owner ON locali(owner_user_id);

CREATE TABLE IF NOT EXISTS bersagli (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  locale_id              INTEGER NOT NULL REFERENCES locali(id) ON DELETE CASCADE,
  identificativo_locale  TEXT NOT NULL,
  game_type_id           INTEGER NOT NULL,
  stato                  TEXT NOT NULL DEFAULT 'DISPONIBILE'
                           CHECK (stato IN ('DISPONIBILE','IN_USO','MANUTENZIONE','OFFLINE')),
  created_at             TEXT NOT NULL,
  UNIQUE (locale_id, identificativo_locale)
);
CREATE INDEX IF NOT EXISTS idx_bersagli_locale ON bersagli(locale_id);

CREATE TABLE IF NOT EXISTS edge_devices (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  dartboard_id           INTEGER NOT NULL UNIQUE REFERENCES bersagli(id) ON DELETE CASCADE,
  locale_id              INTEGER NOT NULL REFERENCES locali(id) ON DELETE CASCADE,
  serial_number          TEXT NOT NULL UNIQUE,
  intervallo_heartbeat_s INTEGER NOT NULL DEFAULT 30,
  soglia_offline_s       INTEGER NOT NULL DEFAULT 90,
  qos                    INTEGER NOT NULL DEFAULT 2 CHECK (qos IN (0,1,2)),
  url_broker_locale      TEXT,
  ultimo_heartbeat       TEXT,
  tiri_in_coda           INTEGER NOT NULL DEFAULT 0,
  stato                  TEXT NOT NULL DEFAULT 'OFFLINE'
                           CHECK (stato IN ('ONLINE','OFFLINE')),
  device_user_id         INTEGER
);
CREATE INDEX IF NOT EXISTS idx_edge_locale ON edge_devices(locale_id);
