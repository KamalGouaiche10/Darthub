-- @version 1
ALTER TABLE game_types ADD COLUMN motore TEXT NOT NULL DEFAULT 'darts';
ALTER TABLE scoring_rules ADD COLUMN parametri_json TEXT NOT NULL DEFAULT '{}';

-- @version 2
DELETE FROM game_types
WHERE nome = char(67, 97, 108, 99, 105, 111, 98, 97, 108, 105, 108, 108, 97);
