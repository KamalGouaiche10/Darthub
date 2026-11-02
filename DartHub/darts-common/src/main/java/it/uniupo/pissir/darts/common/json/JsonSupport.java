package it.uniupo.pissir.darts.common.json;

/**
 * Converte oggetti e testi JSON usando la configurazione condivisa. 
 */
public final class JsonSupport {

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private JsonSupport() {
    }

    /**
     * Serializza il valore nel formato JSON condiviso.
     *
     * @param obj oggetto JSON da leggere
     * @return il contenuto serializzato in JSON
     *  
     */
    public static String toJson(Object obj) {
        return GsonFactory.instance().toJson(obj);
    }

    /**
     * Deserializza il testo JSON nel tipo richiesto.
     *
     * @param <T> tipo degli elementi gestiti
     * @param json testo JSON da deserializzare
     * @param type tipo da selezionare
     * @return l'oggetto {@code T} prodotto o recuperato
     *  
     */
    public static <T> T fromJson(String json, Class<T> type) {
        return GsonFactory.instance().fromJson(json, type);
    }

}
