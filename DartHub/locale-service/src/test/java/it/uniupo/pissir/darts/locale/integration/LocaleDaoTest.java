package it.uniupo.pissir.darts.locale.integration;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.locale.dao.LocaleDao;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code LocaleDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class LocaleDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private LocaleDao localeDao;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        localeDao = new LocaleDao(connectionProvider);
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
        Locale inserito = localeDao.insert(nuovoLocale("Bar Belvedere", 3L, LocaleStatus.APPROVATO));

        Optional<Locale> trovato = localeDao.findById(inserito.id());

        assertTrue(trovato.isPresent());
        assertEquals("Bar Belvedere", trovato.get().nome());
        assertEquals(LocaleType.PUBBLICO, trovato.get().tipo());
        assertEquals(LocaleStatus.APPROVATO, trovato.get().stato());
    }

    /**
     * Verifica {@code findById} nel caso inesistente vuoto. 
     */
    @Test
    void findById_inesistente_vuoto() {
        assertTrue(localeDao.findById(999L).isEmpty());
    }

    /**
     * Verifica {@code search} nel caso filtra per proprietario e stato. 
     */
    @Test
    void search_filtraPerOwnerEStato() {
        localeDao.insert(nuovoLocale("Locale A", 3L, LocaleStatus.APPROVATO));
        localeDao.insert(nuovoLocale("Locale B", 3L, LocaleStatus.IN_ATTESA));
        localeDao.insert(nuovoLocale("Locale C", 4L, LocaleStatus.APPROVATO));

        List<Locale> diOwner3 = localeDao.search(3L, null, Page.of(0, 20));
        List<Locale> approvati = localeDao.search(null, LocaleStatus.APPROVATO, Page.of(0, 20));

        assertEquals(2, diOwner3.size());
        assertEquals(2, approvati.size());
    }

    /**
     * Verifica {@code countSearch} nel caso conta solo i filtrati. 
     */
    @Test
    void countSearch_contaSoloIFiltrati() {
        localeDao.insert(nuovoLocale("Locale A", 3L, LocaleStatus.APPROVATO));
        localeDao.insert(nuovoLocale("Locale B", 3L, LocaleStatus.IN_ATTESA));

        assertEquals(1, localeDao.countSearch(3L, LocaleStatus.APPROVATO));
        assertEquals(2, localeDao.countSearch(3L, null));
    }

    /**
     * Verifica {@code update} nel caso modifica nome indirizzo tipo stato. 
     */
    @Test
    void update_modificaNomeIndirizzoTipoStato() {
        Locale inserito = localeDao.insert(nuovoLocale("Vecchio", 3L, LocaleStatus.IN_ATTESA));
        Locale modificato = new Locale(inserito.id(), "Nuovo", "Nuovo indirizzo", LocaleType.PRIVATO,
                inserito.ownerUserId(), LocaleStatus.APPROVATO, inserito.createdAt());

        localeDao.update(modificato);

        Locale rileto = localeDao.findById(inserito.id()).orElseThrow();
        assertEquals("Nuovo", rileto.nome());
        assertEquals(LocaleType.PRIVATO, rileto.tipo());
        assertEquals(LocaleStatus.APPROVATO, rileto.stato());
    }

    /**
     * Verifica {@code delete} nel caso rimuove locale. 
     */
    @Test
    void delete_rimuoveLocale() {
        Locale inserito = localeDao.insert(nuovoLocale("Da eliminare", 3L, LocaleStatus.APPROVATO));

        localeDao.delete(inserito.id());

        assertTrue(localeDao.findById(inserito.id()).isEmpty());
    }

    /**
     * Verifica {@code count} nel caso conta tutti i locali. 
     */
    @Test
    void count_contaTuttiILocali() {
        localeDao.insert(nuovoLocale("A", 3L, LocaleStatus.APPROVATO));
        localeDao.insert(nuovoLocale("B", 4L, LocaleStatus.APPROVATO));

        assertEquals(2, localeDao.count());
    }

    /**
     * Prepara il locale con i dati richiesti dal caso di prova.
     *
     * @param nome nome della risorsa
     * @param ownerUserId identificativo del proprietario del locale
     * @param stato stato da impostare o verificare
     * @return l'oggetto {@link it.uniupo.pissir.darts.locale.model.Locale} prodotto o recuperato
     * @see it.uniupo.pissir.darts.locale.model.Locale
     *  
     */
    private static Locale nuovoLocale(String nome, long ownerUserId, LocaleStatus stato) {
        return new Locale(0, nome, "Via Test", LocaleType.PUBBLICO, ownerUserId, stato, Instant.now());
    }
}
