package it.uniupo.pissir.darts.match.unit;

import it.uniupo.pissir.darts.match.service.MatchLockRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code MatchLockRegistry} con casi di prova JUnit.
 *
 * @see it.uniupo.pissir.darts.match.service.MatchLockRegistry
 *  
 */
class MatchLockRegistryTest {

    private final MatchLockRegistry registry = new MatchLockRegistry();

    /**
     * Verifica {@code lockFor} nel caso stesso partita identificativo restituisce la stessa istanza. 
     */
    @Test
    void lockFor_stessoMatchId_restituisceLaStessaIstanza() {
        ReentrantLock primo = registry.lockFor(1L);
        ReentrantLock secondo = registry.lockFor(1L);

        assertSame(primo, secondo);
    }

    /**
     * Verifica {@code lockFor} nel caso partita identificativo diversi restituisce istanze diverse. 
     */
    @Test
    void lockFor_matchIdDiversi_restituisceIstanzeDiverse() {
        ReentrantLock lockPartita1 = registry.lockFor(1L);
        ReentrantLock lockPartita2 = registry.lockFor(2L);

        assertNotSame(lockPartita1, lockPartita2);
    }

    /**
     * Verifica {@code lockFor} nel caso e reentrant stesso thread puo riacquisire. 
     */
    @Test
    void lockFor_eReentrant_stessoThreadPuoRiacquisire() {
        ReentrantLock lock = registry.lockFor(1L);
        lock.lock();
        try {
            assertTrue(lock.tryLock());
            lock.unlock();
        } finally {
            lock.unlock();
        }
    }
}
