package it.uniupo.pissir.darts.common.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Fornisce la configurazione Gson condivisa dalla piattaforma.
 *
 * @see com.google.gson.Gson
 *  
 */
public final class GsonFactory {

    private static final Gson INSTANCE = build();

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private GsonFactory() {
    }

    /**
     * Costruisce e restituisce l'oggetto richiesto.
     *
     * @return l'oggetto {@link com.google.gson.Gson} prodotto o recuperato
     * @see com.google.gson.Gson
     *  
     */
    public static Gson instance() {
        return INSTANCE;
    }

    /**
     * Costruisce e restituisce l'oggetto richiesto.
     *
     * @return l'oggetto {@link com.google.gson.Gson} prodotto o recuperato
     * @see com.google.gson.Gson
     *  
     */
    private static Gson build() {
        return new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                .create();
    }

    /**
     * Implementa gson factory.instant type adapter.
     *
     * @see com.google.gson.TypeAdapter
     *  
     */
    private static final class InstantTypeAdapter extends TypeAdapter<Instant> {

        /**
         * Scrive i dati della risposta o della risorsa.
         *
         * @param out flusso o dato in uscita
         * @param value dato {@link java.time.Instant} da elaborare
         * @throws IOException se la lettura, la scrittura o il collegamento a una risorsa non riesce
         * @see com.google.gson.stream.JsonWriter
         *  
         */
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(DateTimeFormatter.ISO_INSTANT.format(value));
            }
        }

        /**
         * Carica il instant tipo adapter dalla sorgente configurata.
         *
         * @param in flusso o dato in ingresso
         * @return l'oggetto {@link java.time.Instant} prodotto o recuperato
         * @throws IOException se la lettura, la scrittura o il collegamento a una risorsa non riesce
         * @see java.time.Instant
         *  
         */
        @Override
        public Instant read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.parse(in.nextString());
        }
    }
}
