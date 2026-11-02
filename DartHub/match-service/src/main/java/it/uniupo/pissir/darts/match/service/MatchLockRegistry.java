package it.uniupo.pissir.darts.match.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Associa a ciascuna partita il lock usato per serializzare gli aggiornamenti.
 *
 * @see java.util.Map
 *  
 */
public final class MatchLockRegistry {

    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Restituisce il lock associato alla partita.
     *
     * @param matchId identificativo della partita
     * @return il lock da acquisire prima di modificare la partita
     * @see java.util.concurrent.locks.ReentrantLock
     *  
     */
    public ReentrantLock lockFor(long matchId) {
        return locks.computeIfAbsent(matchId, id -> new ReentrantLock());
    }
}
