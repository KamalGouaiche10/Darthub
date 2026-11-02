package it.uniupo.pissir.darts.tournament.integration;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.Page;
import it.uniupo.pissir.darts.common.persistence.MigrationRunner;
import it.uniupo.pissir.darts.common.persistence.SchemaInitializer;
import it.uniupo.pissir.darts.tournament.dao.TournamentDao;
import it.uniupo.pissir.darts.tournament.dao.TournamentLocaleDao;
import it.uniupo.pissir.darts.tournament.model.FormulaTorneo;
import it.uniupo.pissir.darts.tournament.model.ModalitaTorneo;
import it.uniupo.pissir.darts.tournament.model.Tournament;
import it.uniupo.pissir.darts.tournament.model.TournamentStatus;
import it.uniupo.pissir.darts.tournament.support.TestDatabaseSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code TournamentDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class TournamentDaoTest {

    @TempDir
    Path tempDir;

    private ConnectionProvider connectionProvider;
    private TournamentDao tournamentDao;
    private TournamentLocaleDao tournamentLocaleDao;

    /**
     * Prepara dati e dipendenze prima di ciascun test. 
     */
    @BeforeEach
    void setUp() {
        connectionProvider = TestDatabaseSupport.freshDatabase(tempDir);
        tournamentDao = new TournamentDao(connectionProvider);
        tournamentLocaleDao = new TournamentLocaleDao(connectionProvider);
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
        Tournament inserito = tournamentDao.insert(nuovoTorneo("Torneo estivo"));

        Tournament rileto = tournamentDao.findById(inserito.id()).orElseThrow();
        assertEquals("Torneo estivo", rileto.nome());
        assertEquals(TournamentStatus.APERTO, rileto.stato());
    }

    /**
     * Verifica il caso migrazione conserva tornei esistenti e data effettive. 
     */
    @Test
    void migrazioneConservaTorneiEsistentiEDateEffettive() {
        ConnectionProvider legacy = new ConnectionProvider(tempDir.resolve("legacy.db").toString(), 2);
        try {
            SchemaInitializer.initialize(legacy, "schema.sql");
            legacy.withTransaction(connection -> {
                try (var ps = connection.prepareStatement("INSERT INTO tournaments(nome, game_type_id, modalita, "
                        + "formula, data_inizio, data_fine, stato) VALUES ('Storico', 1, 'INDIVIDUALE', "
                        + "'GIRONE', '2025-01-01T10:00:00Z', '2025-01-02T12:00:00Z', 'CHIUSO')")) {
                    ps.executeUpdate();
                    return null;
                } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
            });
            MigrationRunner.run(legacy, "migrations");
            Tournament result = new TournamentDao(legacy).findById(1L).orElseThrow();
            assertEquals("Storico", result.nome());
            assertEquals(Instant.parse("2025-01-01T10:00:00Z"), result.dataInizio());
            assertEquals(Instant.parse("2025-01-02T12:00:00Z"), result.dataFineEffettiva());
        } finally {
            legacy.close();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void migrazioneConsolidataCompletaDatabaseParzialmenteAggiornato(int versione) {
        try (ConnectionProvider legacy = new ConnectionProvider(tempDir.resolve("partial.db").toString(), 2)) {
            SchemaInitializer.initialize(legacy, "schema.sql");
            legacy.withTransaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE tournaments ADD COLUMN data_fine_programmata TEXT");
                    statement.execute("ALTER TABLE tournaments ADD COLUMN data_inizio_effettiva TEXT");
                    statement.execute("ALTER TABLE tournaments ADD COLUMN data_fine_effettiva TEXT");
                    statement.execute("INSERT INTO schema_version VALUES (1, '2026-01-01')");
                    statement.execute("INSERT INTO tournaments(nome, game_type_id, modalita, data_inizio, "
                            + "data_inizio_effettiva) VALUES ('Esistente', 1, 'INDIVIDUALE', "
                            + "'2025-01-01T10:00:00Z', '2025-01-01T11:00:00Z')");
                    if (versione == 2) {
                        statement.execute("ALTER TABLE tournament_matches ADD COLUMN home_participant_id INTEGER");
                        statement.execute("ALTER TABLE tournament_matches ADD COLUMN away_participant_id INTEGER");
                        statement.execute("ALTER TABLE tournament_matches ADD COLUMN locale_id INTEGER");
                        statement.execute("CREATE TABLE pairing_notice_reads (user_id INTEGER NOT NULL, "
                                + "match_id INTEGER NOT NULL, read_at TEXT NOT NULL, PRIMARY KEY (user_id, match_id))");
                        statement.execute("INSERT INTO schema_version VALUES (2, '2026-01-01')");
                    }
                    return null;
                } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
            });

            MigrationRunner.run(legacy, "migrations");
            MigrationRunner.run(legacy, "migrations");

            assertEquals(Instant.parse("2025-01-01T11:00:00Z"),
                    new TournamentDao(legacy).findById(1L).orElseThrow().dataInizioEffettiva());
            legacy.withConnection(connection -> {
                try (var statement = connection.createStatement()) {
                    try (var result = statement.executeQuery("SELECT COUNT(*) FROM schema_version")) {
                        assertEquals(4, result.getInt(1));
                    }
                    statement.executeQuery("SELECT home_participant_id, away_participant_id, locale_id FROM tournament_matches").close();
                    statement.executeQuery("SELECT * FROM pairing_notice_reads").close();
                    statement.executeQuery("SELECT * FROM match_appointments").close();
                    statement.executeQuery("SELECT * FROM processed_matches").close();
                    statement.executeQuery("SELECT * FROM match_acceptances").close();
                    return null;
                } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
            });
        }
    }

    @Test
    void migrazioneConsolidataNonRiapplicaVersioniGiaPresenti() {
        Tournament torneo = tournamentDao.insert(nuovoTorneo("Gia aggiornato"));
        MigrationRunner.run(connectionProvider, "migrations");
        assertEquals("Gia aggiornato", tournamentDao.findById(torneo.id()).orElseThrow().nome());
    }

    /**
     * Verifica {@code update} nel caso chiude il torneo con vincitore. 
     */
    @Test
    void update_chiudeIlTorneoConVincitore() {
        Tournament inserito = tournamentDao.insert(nuovoTorneo("Torneo"));
        Tournament chiuso = new Tournament(inserito.id(), inserito.nome(), inserito.gameTypeId(), inserito.modalita(),
                inserito.formula(), inserito.puntiVittoria(), inserito.puntiPareggio(), inserito.puntiSconfitta(),
                inserito.dataInizio(), Instant.now(), TournamentStatus.CHIUSO, 7L);

        tournamentDao.update(chiuso);

        Tournament rileto = tournamentDao.findById(inserito.id()).orElseThrow();
        assertEquals(TournamentStatus.CHIUSO, rileto.stato());
        assertEquals(7L, rileto.vincitoreParticipantId());
    }

    /**
     * Verifica {@code delete} nel caso rimuove torneo. 
     */
    @Test
    void delete_rimuoveTorneo() {
        Tournament inserito = tournamentDao.insert(nuovoTorneo("Da eliminare"));

        tournamentDao.delete(inserito.id());

        assertTrue(tournamentDao.findById(inserito.id()).isEmpty());
    }

    /**
     * Verifica {@code findAll} nel caso ordinato dal piu recente. 
     */
    @Test
    void findAll_ordinatoDalPiuRecente() {
        Tournament primo = tournamentDao.insert(nuovoTorneo("Primo"));
        Tournament secondo = tournamentDao.insert(nuovoTorneo("Secondo"));

        List<Tournament> lista = tournamentDao.findAll(Page.of(0, 20));

        assertEquals(secondo.id(), lista.get(0).id());
        assertEquals(primo.id(), lista.get(1).id());
        assertEquals(2, tournamentDao.count());
    }

    /**
     * Verifica {@code findDueToCloseIds} nel caso tornei aperti o in corso con fine trascorsa. 
     */
    @Test
    void findDueToCloseIds_torneiApertiOInCorsoConFineTrascorsa() {
        Instant now = Instant.parse("2026-10-09T12:00:00Z");
        Tournament passato = nuovoTorneo("Passato");
        Tournament inCorso = tournamentDao.insert(new Tournament(0, passato.nome(), passato.gameTypeId(),
                passato.modalita(), passato.formula(), passato.puntiVittoria(), passato.puntiPareggio(),
                passato.puntiSconfitta(), passato.dataInizio(), now.minusSeconds(1), TournamentStatus.IN_CORSO, null));
        tournamentDao.insert(new Tournament(0, "Futuro", passato.gameTypeId(), passato.modalita(),
                passato.formula(), 3, 1, 0, passato.dataInizio(), now.plusSeconds(1),
                TournamentStatus.IN_CORSO, null));
        Tournament aperto = tournamentDao.insert(new Tournament(0, "Aperto", passato.gameTypeId(), passato.modalita(),
                passato.formula(), 3, 1, 0, passato.dataInizio(), now.minusSeconds(1),
                TournamentStatus.APERTO, null));
        tournamentDao.insert(new Tournament(0, "Chiuso", passato.gameTypeId(), passato.modalita(),
                passato.formula(), 3, 1, 0, passato.dataInizio(), now.minusSeconds(1),
                TournamentStatus.CHIUSO, null));
        Tournament alLimite = tournamentDao.insert(new Tournament(0, "Al limite", passato.gameTypeId(), passato.modalita(),
                passato.formula(), 3, 1, 0, passato.dataInizio(), now, TournamentStatus.IN_CORSO, null));

        assertEquals(List.of(inCorso.id(), aperto.id(), alLimite.id()), tournamentDao.findDueToCloseIds(now));
    }

    /**
     * Verifica {@code tournamentLocale} nel caso insert find delete e cascade alla cancellazione del torneo. 
     */
    @Test
    void tournamentLocale_insertFindDelete_eCascadeAllaCancellazioneDelTorneo() {
        Tournament torneo = tournamentDao.insert(nuovoTorneo("Torneo"));

        tournamentLocaleDao.insert(torneo.id(), 9L);
        tournamentLocaleDao.insert(torneo.id(), 10L);

        assertEquals(List.of(9L, 10L), tournamentLocaleDao.findLocaliIds(torneo.id()));
        assertTrue(tournamentLocaleDao.isAssociato(torneo.id(), 9L));

        tournamentLocaleDao.delete(torneo.id(), 9L);
        assertFalse(tournamentLocaleDao.isAssociato(torneo.id(), 9L));

        tournamentDao.delete(torneo.id());
        assertTrue(tournamentLocaleDao.findLocaliIds(torneo.id()).isEmpty());
    }

    /**
     * Verifica {@code tournamentLocale} nel caso insert o ignore non duplica. 
     */
    @Test
    void tournamentLocale_insertOrIgnore_nonDuplica() {
        Tournament torneo = tournamentDao.insert(nuovoTorneo("Torneo"));

        tournamentLocaleDao.insert(torneo.id(), 9L);
        tournamentLocaleDao.insert(torneo.id(), 9L);

        assertEquals(1, tournamentLocaleDao.findLocaliIds(torneo.id()).size());
    }

    /**
     * Prepara il torneo con i dati richiesti dal caso di prova.
     *
     * @param nome nome della risorsa
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.model.Tournament} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.model.Tournament
     *  
     */
    private static Tournament nuovoTorneo(String nome) {
        return new Tournament(0, nome, 1L, ModalitaTorneo.INDIVIDUALE, FormulaTorneo.GIRONE, 3, 1, 0,
                Instant.now(), null, TournamentStatus.APERTO, null);
    }
}
