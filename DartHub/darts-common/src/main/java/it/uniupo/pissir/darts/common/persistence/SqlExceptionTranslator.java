package it.uniupo.pissir.darts.common.persistence;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.exception.ValidationException;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import java.sql.SQLException;

/**
 * Traduce gli errori SQL nelle eccezioni applicative corrispondenti. 
 */
public final class SqlExceptionTranslator {

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private SqlExceptionTranslator() {
    }

    /**
     * Traduce l'eccezione SQL nella corrispondente eccezione applicativa.
     *
     * @param e eccezione da gestire
     * @return l'oggetto {@code RuntimeException} prodotto o recuperato
     * @see java.sql.SQLException
     *  
     */
    public static RuntimeException translate(SQLException e) {
        if (!(e instanceof SQLiteException sqliteException)) {
            return new IllegalStateException("Errore SQL non gestito", e);
        }
        SQLiteErrorCode resultCode = sqliteException.getResultCode();
        return switch (resultCode) {
            case SQLITE_CONSTRAINT_UNIQUE, SQLITE_CONSTRAINT_PRIMARYKEY ->
                    new ConflictException("Violazione di un vincolo di unicità", e);
            case SQLITE_CONSTRAINT_FOREIGNKEY ->
                    new ValidationException("Riferimento a una risorsa inesistente", e);
            case SQLITE_CONSTRAINT_CHECK ->
                    new ValidationException("Valore non ammesso da un vincolo CHECK", e);
            default ->
                    new IllegalStateException("Errore SQL non gestito (codice " + resultCode + ")", e);
        };
    }
}
