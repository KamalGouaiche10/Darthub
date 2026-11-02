package it.uniupo.pissir.darts.tournament.dto;

/**
 * Raccoglie i dati di abbinamento.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param matchId identificativo della partita
 * @param tournamentId identificativo del torneo
 * @param torneo torneo a cui si riferiscono i dati
 * @param turno turno da elaborare
 * @param locale locale a cui si riferiscono i dati
 * @param incontro incontro del torneo
 * @param avversario partecipante avversario
 * @param riposo partecipante che riposa nel turno
 * @param letto indicatore di lettura dell'avviso
 * @param scheduledAt istante programmato per l'inizio della partita
 * @param linkAt istante da cui il collegamento alla partita Ã¨ disponibile
 * @param deadline istante limite per la conferma della presenza
 * @param outcome esito della partita
 * @param stato stato da impostare o verificare
 * @param homeReady indicatore di presenza del partecipante di casa
 * @param awayReady indicatore di presenza del partecipante ospite
 * @param linkAvailable indicatore di disponibilitÃ  del collegamento alla partita
 * @param esitoAssenza esito assegnato per l'assenza dei partecipanti
 * @param homeAccepted indicatore di accettazione del partecipante di casa
 * @param awayAccepted indicatore di accettazione del partecipante ospite
 * @param individual indicatore di partecipazione individuale
 * @param acceptedByMe indicatore di accettazione da parte dell'utente corrente
 *  
 */
public record PairingDTO(long matchId, long tournamentId, String torneo, int turno, String locale,
                         String incontro, String avversario, boolean riposo, boolean letto,
                         String scheduledAt, String linkAt, String deadline, String outcome,
                         String stato, boolean homeReady, boolean awayReady, boolean linkAvailable,
                         String esitoAssenza, boolean homeAccepted, boolean awayAccepted, boolean individual,
                         boolean acceptedByMe) {
    /**
     * Inizializza {@code PairingDTO} con i dati e le dipendenze indicati.
     *
     * @param matchId identificativo della partita
     * @param tournamentId identificativo del torneo
     * @param torneo torneo a cui si riferiscono i dati
     * @param turno turno da elaborare
     * @param locale locale a cui si riferiscono i dati
     * @param incontro incontro del torneo
     * @param avversario partecipante avversario
     * @param riposo partecipante che riposa nel turno
     * @param letto indicatore di lettura dell'avviso
     *  
     */
    public PairingDTO(long matchId, long tournamentId, String torneo, int turno, String locale,
                      String incontro, String avversario, boolean riposo, boolean letto) {
        this(matchId, tournamentId, torneo, turno, locale, incontro, avversario, riposo, letto,
                null, null, null, null, null, false, false, false, null, false, false, false, false);
    }
}
