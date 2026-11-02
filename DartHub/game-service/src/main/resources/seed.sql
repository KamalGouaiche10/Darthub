
INSERT OR IGNORE INTO game_types(nome, descrizione, motore) VALUES
  ('Freccette', 'Gioco delle freccette su bersaglio standard, sensore per settore e moltiplicatore', 'darts');

INSERT OR IGNORE INTO sensor_types(game_type_id, nome, formato_dato, modalita)
SELECT id, 'Sensore punteggio tiro', '{settore, moltiplicatore}', 'EMULATO'
FROM game_types WHERE nome = 'Freccette';

INSERT OR IGNORE INTO scoring_rules(game_type_id, variante, direzione, punteggio_iniziale,
                                     punteggio_obiettivo, chiusura_double, bust_annulla_turno, max_tiri_per_turno)
SELECT id, '501', 'DECRESCENTE', 501, 0, 1, 1, 3 FROM game_types WHERE nome = 'Freccette';

INSERT OR IGNORE INTO scoring_rules(game_type_id, variante, direzione, punteggio_iniziale,
                                     punteggio_obiettivo, chiusura_double, bust_annulla_turno, max_tiri_per_turno)
SELECT id, '301', 'DECRESCENTE', 301, 0, 1, 1, 3 FROM game_types WHERE nome = 'Freccette';
