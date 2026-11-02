package it.uniupo.pissir.darts.edge.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica il comportamento di {@code LocalScoreController} con casi di prova JUnit. 
 */
class LocalScoreControllerTest {

    /**
     * Verifica il caso locale commands require separate display token. 
     */
    @Test
    void localCommandsRequireSeparateDisplayToken() {
        LocalScoreController controller = new LocalScoreController(null, null, "display-secret");
        assertFalse(controller.isAdmin(null));
        assertFalse(controller.isAdmin("wrong-secret"));
        assertTrue(controller.isAdmin("display-secret"));
        assertFalse(new LocalScoreController(null, null, "").isAdmin("anything"));
    }
}
