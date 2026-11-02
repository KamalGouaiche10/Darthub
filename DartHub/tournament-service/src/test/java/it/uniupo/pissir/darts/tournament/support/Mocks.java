package it.uniupo.pissir.darts.tournament.support;

import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import org.mockito.stubbing.Answer;

import java.sql.Connection;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Prepara il comportamento delle dipendenze simulate nei test. 
 */
public final class Mocks {

    /**
     * Impedisce l'istanziazione della classe di utilità. 
     */
    private Mocks() {
    }

    /**
     * Fa eseguire ai mock le operazioni ricevute sulle connessioni di prova.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public static void passThroughTransactionsAndConnections(ConnectionProvider connectionProvider) {
        Connection dummyConnection = mock(Connection.class);
        Answer<Object> runGivenFunction = invocation -> {
            Function<Connection, Object> action = invocation.getArgument(0);
            return action.apply(dummyConnection);
        };
        lenient().when(connectionProvider.withTransaction(any())).thenAnswer(runGivenFunction);
        lenient().when(connectionProvider.withConnection(any())).thenAnswer(runGivenFunction);
    }
}
