package it.uniupo.pissir.darts.common.http;

import it.uniupo.pissir.darts.common.exception.ValidationException;
import it.uniupo.pissir.darts.common.persistence.Page;
import spark.Request;

import java.time.Instant;

/**
 * Legge e valida i parametri HTTP condivisi dai controller dei servizi.
 */
public final class ControllerSupport {

    /**
     * Impedisce l'istanziazione della classe di utilità.
     */
    private ControllerSupport() {
    }

    /**
     * Legge un identificativo numerico dal percorso della richiesta.
     *
     * @param req richiesta HTTP ricevuta
     * @param param nome del parametro nel percorso
     * @return identificativo richiesto
     * @throws ValidationException se l'identificativo non è un numero long
     */
    public static long pathLong(Request req, String param) {
        try {
            return Long.parseLong(req.params(":" + param));
        } catch (NumberFormatException e) {
            throw new ValidationException("Identificativo non valido: " + param);
        }
    }

    /**
     * Legge un intero facoltativo dalla query della richiesta.
     *
     * @param req richiesta HTTP ricevuta
     * @param param nome del parametro
     * @return valore letto, oppure {@code null} se assente o vuoto
     * @throws ValidationException se il valore non è un numero intero
     */
    public static Integer queryIntOrNull(Request req, String param) {
        String value = req.queryParams(param);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Parametro non valido: " + param);
        }
    }

    /**
     * Legge un long facoltativo dalla query della richiesta.
     *
     * @param req richiesta HTTP ricevuta
     * @param param nome del parametro
     * @return valore letto, oppure {@code null} se assente o vuoto
     * @throws ValidationException se il valore non è un numero long
     */
    public static Long queryLongOrNull(Request req, String param) {
        String value = req.queryParams(param);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Parametro non valido: " + param);
        }
    }

    /**
     * Legge un istante facoltativo dalla query della richiesta.
     *
     * @param req richiesta HTTP ricevuta
     * @param param nome del parametro
     * @return istante letto, oppure {@code null} se assente o vuoto
     * @throws ValidationException se il valore non è un istante ISO valido
     */
    public static Instant queryInstantOrNull(Request req, String param) {
        String value = req.queryParams(param);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (RuntimeException e) {
            throw new ValidationException("Parametro data non valido: " + param);
        }
    }

    /**
     * Verifica se il parametro della query contiene il valore true.
     *
     * @param req richiesta HTTP ricevuta
     * @param param nome del parametro
     * @return {@code true} per il valore true, ignorando le maiuscole
     */
    public static boolean queryBoolean(Request req, String param) {
        return "true".equalsIgnoreCase(req.queryParams(param));
    }

    /**
     * Legge indice e dimensione della pagina applicando i valori predefiniti.
     *
     * @param req richiesta HTTP ricevuta
     * @return paginazione richiesta
     */
    public static Page page(Request req) {
        return Page.of(queryIntOrNull(req, "page"), queryIntOrNull(req, "size"));
    }
}

