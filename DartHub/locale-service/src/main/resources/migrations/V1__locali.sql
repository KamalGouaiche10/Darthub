-- @version 1
ALTER TABLE locali ADD COLUMN stato TEXT NOT NULL DEFAULT 'APPROVATO';

-- @version 2
DELETE FROM bersagli
WHERE identificativo_locale = 'B2' AND game_type_id = 2
  AND locale_id IN (SELECT id FROM locali WHERE nome = 'Circolo Privato Frecce');

-- @version 3
ALTER TABLE bersagli ADD COLUMN offline_due_to_edge INTEGER NOT NULL DEFAULT 0
    CHECK (offline_due_to_edge IN (0, 1));

-- @version 4
DELETE FROM edge_devices
WHERE serial_number = 'EDGE-BELVEDERE-01'
  AND device_user_id IS NULL
  AND ultimo_heartbeat IS NULL
  AND stato = 'OFFLINE';

-- @version 5
DELETE FROM edge_devices
WHERE serial_number = 'EDGE-BELVEDERE-01'
  AND device_user_id IS NULL
  AND stato = 'OFFLINE';
