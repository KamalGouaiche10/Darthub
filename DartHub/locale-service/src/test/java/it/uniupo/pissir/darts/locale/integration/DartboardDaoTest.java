package it.uniupo.pissir.darts.locale.integration;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.locale.dao.DartboardDao;
import it.uniupo.pissir.darts.locale.dao.LocaleDao;
import it.uniupo.pissir.darts.locale.model.Dartboard;
import it.uniupo.pissir.darts.locale.model.DartboardStatus;
import it.uniupo.pissir.darts.locale.model.Locale;
import it.uniupo.pissir.darts.locale.model.LocaleStatus;
import it.uniupo.pissir.darts.locale.model.LocaleType;
import it.uniupo.pissir.darts.locale.support.TestDatabaseSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code DartboardDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class DartboardDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private LocaleDao localeDao;
    private DartboardDao dartboardDao;
    private long localeId;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        localeDao = new LocaleDao(connectionProvider);
        dartboardDao = new DartboardDao(connectionProvider);
        localeId = localeDao.insert(new Locale(0, "Bar Belvedere", "Via Test", LocaleType.PUBBLICO, 3L,
                LocaleStatus.APPROVATO, Instant.now())).id();
    }

    /**
     * Chiude le risorse aperte durante il test. 
     */
    @AfterEach
    void tearDown() {
        connectionProvider.close();
    }

    /**
     * Verifica {@code insertEFindById} nel caso round trip. 
     */
    @Test
    void insertEFindById_roundTrip() {
        Dartboard inserito = dartboardDao.insert(nuovoBersaglio("B1"));

        assertEquals("B1", dartboardDao.findById(inserito.id()).orElseThrow().identificativoLocale());
    }

    /**
     * Verifica {@code insert} nel caso identificativo duplicato nel locale conflict exception. 
     */
    @Test
    void insert_identificativoDuplicatoNelLocale_conflictException() {
        dartboardDao.insert(nuovoBersaglio("B1"));

        ConflictException e = assertThrows(ConflictException.class, () -> dartboardDao.insert(nuovoBersaglio("B1")));
        assertEquals("DARTBOARD_DUPLICATO", e.codice());
    }

    /**
     * Verifica {@code insert} nel caso stesso identificativo in locali diversi ok. 
     */
    @Test
    void insert_stessoIdentificativoInLocaliDiversi_ok() {
        long altroLocaleId = localeDao.insert(new Locale(0, "Altro locale", "Via Y", LocaleType.PRIVATO, 4L,
                LocaleStatus.APPROVATO, Instant.now())).id();

        dartboardDao.insert(nuovoBersaglio("B1"));
        Dartboard secondo = dartboardDao.insert(new Dartboard(0, altroLocaleId, "B1", 5L,
                DartboardStatus.DISPONIBILE, Instant.now()));

        assertEquals("B1", secondo.identificativoLocale());
    }

    /**
     * Verifica {@code findByLocale} nel caso e conteggio per locale. 
     */
    @Test
    void findByLocale_eCountByLocale() {
        dartboardDao.insert(nuovoBersaglio("B1"));
        dartboardDao.insert(nuovoBersaglio("B2"));

        List<Dartboard> lista = dartboardDao.findByLocale(localeId, Page.of(0, 20));

        assertEquals(2, lista.size());
        assertEquals(2, dartboardDao.countByLocale(localeId));
    }

    /**
     * Verifica {@code update} nel caso modifica identificativo e gioco tipo. 
     */
    @Test
    void update_modificaIdentificativoEGameType() {
        Dartboard inserito = dartboardDao.insert(nuovoBersaglio("B1"));
        Dartboard modificato = new Dartboard(inserito.id(), localeId, "B1-nuovo", 9L,
                DartboardStatus.IN_USO, inserito.createdAt());

        dartboardDao.update(modificato);

        Dartboard rileto = dartboardDao.findById(inserito.id()).orElseThrow();
        assertEquals("B1-nuovo", rileto.identificativoLocale());
        assertEquals(9L, rileto.gameTypeId());
        assertEquals(DartboardStatus.IN_USO, rileto.stato());
    }

    /**
     * Verifica {@code delete} nel caso rimuove bersaglio. 
     */
    @Test
    void delete_rimuoveBersaglio() {
        Dartboard inserito = dartboardDao.insert(nuovoBersaglio("B1"));

        dartboardDao.delete(inserito.id());

        assertTrue(dartboardDao.findById(inserito.id()).isEmpty());
    }

    /**
     * Verifica {@code delete} nel caso locale cascada sui bersagli. 
     */
    @Test
    void delete_locale_cascadaSuiBersagli() {
        dartboardDao.insert(nuovoBersaglio("B1"));

        localeDao.delete(localeId);

        assertTrue(dartboardDao.findByLocale(localeId, Page.of(0, 20)).isEmpty());
    }

    /**
     * Prepara il bersaglio con i dati richiesti dal caso di prova.
     *
     * @param identificativo identificativo del bersaglio nel locale
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.Dartboard} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.model.Dartboard
     *  
     */
    private Dartboard nuovoBersaglio(String identificativo) {
        return new Dartboard(0, localeId, identificativo, 5L, DartboardStatus.DISPONIBILE, Instant.now());
    }
}
