package it.uniupo.pissir.darts.edge.service;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;

/**
 * Conserva localmente la regola e i partecipanti della partita.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class RuleCache {

    private final ConnectionProvider connectionProvider;

    /**
     * Inizializza {@code RuleCache} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public RuleCache(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    /**
     * Conserva la regola e la formazione correnti nella cache locale.
     *
     * @param gameTypeId identificativo del gioco
     * @param scoringRuleJson regola di punteggio in JSON
     * @param rosterJson formazione dei partecipanti in JSON
     *  
     */
    public void save(Long gameTypeId, String scoringRuleJson, String rosterJson) {
        connectionProvider.withTransaction(connection -> {
            String sql = "INSERT INTO rule_cache(id, game_type_id, scoring_rule_json, roster_json, updated_at) "
                    + "VALUES (1,?,?,?,?) "
                    + "ON CONFLICT(id) DO UPDATE SET game_type_id = excluded.game_type_id, "
                    + "scoring_rule_json = excluded.scoring_rule_json, roster_json = excluded.roster_json, "
                    + "updated_at = excluded.updated_at";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, gameTypeId);
                ps.setString(2, scoringRuleJson);
                ps.setString(3, rosterJson);
                ps.setString(4, Instant.now().toString());
                ps.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }

    /**
     * Restituisce la regola corrente della partita in JSON.
     *
     * @return la regola corrente serializzata in JSON
     * @see java.util.Optional
     *  
     */
    public Optional<String> currentScoringRuleJson() {
        return leggiColonna("scoring_rule_json");
    }

    /**
     * Legge colonna per la regola di gioco.
     *
     * @param colonna nome della colonna SQL
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    private Optional<String> leggiColonna(String colonna) {
        return connectionProvider.withConnection(connection -> {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT " + colonna + " FROM rule_cache WHERE id = 1")) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String value = rs.getString(1);
                return value == null ? Optional.empty() : Optional.of(value);
            } catch (SQLException e) {
                throw SqlExceptionTranslator.translate(e);
            }
        });
    }
}
