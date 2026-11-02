CREATE TABLE player_visibility (
  player_id INTEGER PRIMARY KEY,
  visibile  INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE locale_visibility (
  locale_id INTEGER PRIMARY KEY,
  visibile  INTEGER NOT NULL DEFAULT 1
);
