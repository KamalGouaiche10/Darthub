package it.uniupo.pissir.darts.edge.service;

import it.uniupo.pissir.darts.edge.dao.BufferedThrowDao;
/**
 * Conserva sull'edge i tiri da sincronizzare con il server.
 *
 * @see BufferedThrowDao
 *  
 */
public final class OfflineStorageService {

    private final BufferedThrowDao bufferedThrowDao;

    /**
     * Inizializza {@code OfflineStorageService} con i dati e le dipendenze indicati.
     *
     * @param bufferedThrowDao componente {@link it.uniupo.pissir.darts.edge.dao.BufferedThrowDao} utilizzato dall'operazione
     * @see BufferedThrowDao
     *  
     */
    public OfflineStorageService(BufferedThrowDao bufferedThrowDao) {
        this.bufferedThrowDao = bufferedThrowDao;
    }

    /**
     * Conta i tiri conservati nel buffer di sincronizzazione.
     *
     * @return numero di tiri in attesa di sincronizzazione
     *  
     */
    public int tiriInCoda() {
        return bufferedThrowDao.countPendenti();
    }
}
