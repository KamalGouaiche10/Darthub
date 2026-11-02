package it.uniupo.pissir.darts.common.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Converte la riga corrente di un risultato JDBC in un oggetto del dominio.
 *
 * @param <T> tipo degli elementi gestiti
 *  
 */
@FunctionalInterface
public interface RowMapper<T> {

    /**
     * Converte i dati ricevuti nel tipo richiesto.
     *
     * @param resultSet risultato JDBC da leggere
     * @return l'oggetto {@code T} prodotto o recuperato
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see java.sql.ResultSet
     *  
     */
    T map(ResultSet resultSet) throws SQLException;
}
