package it.uniupo.pissir.darts.edge.service;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.SchemaInitializer;
import it.uniupo.pissir.darts.edge.dao.BufferedThrowDao;
import it.uniupo.pissir.darts.edge.dao.LocalMatchDao;
import it.uniupo.pissir.darts.edge.model.LocalMatch;
import it.uniupo.pissir.darts.edge.model.LocalMatchOrigin;
import it.uniupo.pissir.darts.edge.model.LocalMatchStatus;
import it.uniupo.pissir.darts.edge.model.RosterEntry;
import it.uniupo.pissir.darts.game.model.darts.DartSector;
import it.uniupo.pissir.darts.game.model.darts.Esito;
import it.uniupo.pissir.darts.game.model.darts.Multiplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code MatchSessionManagerDisplay} con casi di prova JUnit.
 *
 * @see java.nio.file.Path
 *  
 */
class MatchSessionManagerDisplayTest {

    private static final String RULE = """
            {"variante":"501","direzione":"DECRESCENTE","punteggioIniziale":501,
             "punteggioObiettivo":0,"chiusuraDouble":false,"bustAnnullaTurno":true,
             "maxTiriPerTurno":3}
            """;

    @TempDir
    Path tempDir;

    /**
     * Verifica il caso monitor shows turn e full throw history dopo restart. 
     */
    @Test
    void monitorShowsTurnAndFullThrowHistoryAfterRestart() {
        String databasePath = tempDir.resolve("edge.db").toString();
        try (ConnectionProvider provider = new ConnectionProvider(databasePath, 2)) {
            SchemaInitializer.initialize(provider, "schema.sql");
            LocalMatchDao matches = new LocalMatchDao(provider);
            BufferedThrowDao throwsDao = new BufferedThrowDao(provider);
            MatchSessionManager display = new MatchSessionManager(provider, matches, throwsDao);

            var empty = display.statoCorrente();
            assertFalse(empty.inCorso());
            assertTrue(empty.storicoTiri().isEmpty());

            LocalMatch match = new LocalMatch("session-1", 42L, 7L, RULE, LocalMatchOrigin.SERVER,
                    LocalMatchStatus.IN_CORSO, false, Instant.parse("2026-05-11T11:00:00Z"), null);
            matches.insertMatchWithRoster(match,
                    List.of(new RosterEntry(101L, "Ada", 1), new RosterEntry(102L, "Bruno", 2)), 501);
            var participants = matches.findParticipantsByMatch("session-1");
            long firstId = participants.get(0).id();
            long secondId = participants.get(1).id();

            var beforeThrows = display.statoCorrente();
            assertEquals(firstId, beforeThrows.partecipanteAttivoId());
            assertEquals(1, beforeThrows.numeroTurno());
            assertTrue(beforeThrows.storicoTiri().isEmpty());

            provider.withTransaction(connection -> {
                for (int second = 1; second <= 3; second++) {
                    throwsDao.insert(connection, "session-1", firstId, DartSector.S20, Multiplier.SINGLE,
                            20, 1, Esito.VALIDO, false, Instant.parse("2026-05-11T11:00:0" + second + "Z"), false);
                }
                matches.updateParticipantScore(connection, firstId, 441);
                return null;
            });
            var current = display.statoCorrente();
            assertEquals(secondId, current.partecipanteAttivoId());
            assertEquals(1, current.numeroTurno());
            assertEquals(3, current.storicoTiri().size());
            assertEquals("Ada", current.storicoTiri().get(0).giocatore());
            assertEquals("S20", current.storicoTiri().get(0).settore());
            assertEquals("SINGLE", current.storicoTiri().get(0).moltiplicatore());
            assertEquals(441, current.partecipanti().get(0).punteggioCorrente());
        }

        try (ConnectionProvider reopened = new ConnectionProvider(databasePath, 2)) {
            var restored = new MatchSessionManager(reopened, new LocalMatchDao(reopened),
                    new BufferedThrowDao(reopened)).statoCorrente();
            assertEquals(3, restored.storicoTiri().size());
            assertEquals("Bruno", restored.partecipanti().stream()
                    .filter(p -> p.participantId() == restored.partecipanteAttivoId())
                    .findFirst().orElseThrow().nome());
        }
    }
}
