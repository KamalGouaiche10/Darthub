package it.uniupo.pissir.darts.tournament.integration;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao;
import it.uniupo.pissir.darts.tournament.support.TestDatabaseSupport;
import java.time.Instant;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica il comportamento di {@code MatchAppointmentDao} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class MatchAppointmentDaoTest {
    @TempDir Path temp;
    private static final Instant AT = Instant.parse("2030-04-02T14:00:00Z");

    /**
     * Verifica il caso orari utc conflitti presenze separate e idempotenza. 
     */
    @Test void orariUtcConflittiPresenzeSeparateEIdempotenza() {
        try (ConnectionProvider db = TestDatabaseSupport.freshDatabase(temp)) {
            db.withTransaction(c -> {
                try (var ps = c.prepareStatement("INSERT INTO tournaments(id,nome,game_type_id,modalita,formula,data_inizio,stato) "
                        + "VALUES(1,'Test',1,'INDIVIDUALE','GIRONE','2030-04-01T00:00:00Z','IN_CORSO')")) {
                    ps.executeUpdate();
                } catch (Exception e) { throw new IllegalStateException(e); }
                return null;
            });
            MatchAppointmentDao dao = new MatchAppointmentDao(db);
            var first = dao.schedule(1, 10, 5, AT, 60);
            assertEquals(AT, first.scheduledAt());
            assertEquals(AT, first.linkAt());
            assertThrows(ConflictException.class, () -> dao.schedule(1, 11, 5, AT.plusSeconds(1800), 60));
            dao.schedule(1, 11, 5, AT.plusSeconds(3600), 60);
            assertThrows(ConflictException.class, () -> dao.ready(10, true, AT.minusSeconds(601), 5));
            dao.ready(10, true, AT, 5);
            dao.ready(10, true, AT.plusSeconds(100), 5);
            assertNotNull(dao.find(10).homeReadyAt());
            assertNull(dao.find(10).awayReadyAt());
            dao.ready(10, false, AT.plusSeconds(200), 5);
            assertNotNull(dao.find(10).awayReadyAt());
            assertTrue(dao.setOutcome(10, "IN_CORSO", first.revision()));
            assertFalse(dao.setOutcome(10, "IN_CORSO", first.revision() - 1));
            assertTrue(dao.setOutcome(10, "COMPLETATA", first.revision()));
            assertThrows(ConflictException.class, () -> dao.ready(10, true, AT, 5));
        }
    }

    /**
     * Verifica il caso cambio orario azzera le presenze e conserva la revisione. 
     */
    @Test void cambioOrarioAzzeraLePresenzeEConservaLaRevisione() {
        try (ConnectionProvider db = TestDatabaseSupport.freshDatabase(temp)) {
            db.withTransaction(c -> {
                try (var ps = c.prepareStatement("INSERT INTO tournaments(id,nome,game_type_id,modalita,formula,data_inizio,stato) "
                        + "VALUES(1,'Test',1,'INDIVIDUALE','GIRONE','2030-04-01T00:00:00Z','IN_CORSO')")) {
                    ps.executeUpdate();
                } catch (Exception e) { throw new IllegalStateException(e); }
                return null;
            });
            MatchAppointmentDao dao = new MatchAppointmentDao(db);
            dao.schedule(1, 10, 5, AT, 60);
            dao.ready(10, true, AT, 5);
            var changed = dao.schedule(1, 10, 5, AT.plusSeconds(7200), 45);
            assertEquals(1, changed.revision());
            assertNull(changed.homeReadyAt());
            assertFalse(dao.setOutcome(10, "VITTORIA_A_TAVOLINO", 0));
        }
    }

    /**
     * Verifica il caso richieste contemporanee non prenotano due volte il bersaglio.
     *
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     *  
     */
    @Test void richiesteContemporaneeNonPrenotanoDueVolteIlBersaglio() throws Exception {
        try (ConnectionProvider db = TestDatabaseSupport.freshDatabase(temp)) {
            db.withTransaction(c -> {
                try (var ps = c.prepareStatement("INSERT INTO tournaments(id,nome,game_type_id,modalita,formula,data_inizio,stato) "
                        + "VALUES(1,'Test',1,'INDIVIDUALE','GIRONE','2030-04-01T00:00:00Z','IN_CORSO')")) {
                    ps.executeUpdate();
                } catch (Exception e) { throw new IllegalStateException(e); }
                return null;
            });
            MatchAppointmentDao dao = new MatchAppointmentDao(db);
            var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            var gate = new java.util.concurrent.CountDownLatch(1);
            try {
                var one = pool.submit(() -> { gate.await(); return book(dao, 10); });
                var two = pool.submit(() -> { gate.await(); return book(dao, 11); });
                gate.countDown();
                assertEquals(1, (one.get() ? 1 : 0) + (two.get() ? 1 : 0));
            } finally { pool.shutdownNow(); }
        }
    }

    /**
     * Verifica il caso scadenza e ingresso contemporanei hanno un solo esito.
     *
     * @throws Exception se la preparazione o l'esecuzione dell'operazione non riesce
     *  
     */
    @Test void scadenzaEIngressoContemporaneiHannoUnSoloEsito() throws Exception {
        try (ConnectionProvider db = TestDatabaseSupport.freshDatabase(temp)) {
            db.withTransaction(c -> {
                try (var ps = c.prepareStatement("INSERT INTO tournaments(id,nome,game_type_id,modalita,formula,data_inizio,stato) "
                        + "VALUES(1,'Test',1,'INDIVIDUALE','GIRONE','2030-04-01T00:00:00Z','IN_CORSO')")) {
                    ps.executeUpdate();
                } catch (Exception e) { throw new IllegalStateException(e); }
                return null;
            });
            MatchAppointmentDao dao = new MatchAppointmentDao(db);
            dao.schedule(1, 10, 5, AT, 60);
            dao.ready(10, true, AT, 5);
            var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            var gate = new java.util.concurrent.CountDownLatch(1);
            try {
                var arrival = pool.submit(() -> {
                    gate.await();
                    try { dao.ready(10, false, AT.plusSeconds(300), 5); return true; }
                    catch (ConflictException e) { return false; }
                });
                var deadline = pool.submit(() -> { gate.await(); return dao.claimAbsence(10, AT.plusSeconds(300), 5); });
                gate.countDown();
                boolean arrived = arrival.get();
                var claimed = deadline.get();
                assertEquals(arrived, claimed == null);
                assertEquals(arrived, dao.find(10).awayReadyAt() != null);
            } finally { pool.shutdownNow(); }
        }
    }

    /**
     * Prenota l'appuntamento della partita nel caso di prova.
     *
     * @param dao dao da utilizzare
     * @param matchId identificativo della partita
     * @return l'esito della condizione verificata
     * @see it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao
     *  
     */
    private static boolean book(MatchAppointmentDao dao, long matchId) {
        try { dao.schedule(1, matchId, 5, AT, 60); return true; }
        catch (ConflictException e) { return false; }
    }
}
