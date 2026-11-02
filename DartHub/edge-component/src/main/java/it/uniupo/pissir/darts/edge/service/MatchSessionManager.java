package it.uniupo.pissir.darts.edge.service;

import com.google.gson.Gson;
import it.uniupo.pissir.darts.common.json.GsonFactory;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.edge.dao.BufferedThrowDao;
import it.uniupo.pissir.darts.edge.dao.LocalMatchDao;
import it.uniupo.pissir.darts.edge.model.BufferedThrow;
import it.uniupo.pissir.darts.edge.model.LocalMatch;
import it.uniupo.pissir.darts.edge.model.LocalMatchStatus;
import it.uniupo.pissir.darts.edge.model.LocalParticipant;
import it.uniupo.pissir.darts.edge.model.ScoringRuleSnapshot;
import it.uniupo.pissir.darts.game.model.Direzione;
import it.uniupo.pissir.darts.game.model.ScoringRule;
import it.uniupo.pissir.darts.game.model.darts.DartSector;
import it.uniupo.pissir.darts.game.model.darts.DartsRuleEngine;
import it.uniupo.pissir.darts.game.model.darts.Esito;
import it.uniupo.pissir.darts.game.model.darts.Multiplier;
import it.uniupo.pissir.darts.game.model.darts.ThrowOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gestisce sull'edge lo stato della partita e il partecipante attivo. 
 */
public final class MatchSessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(MatchSessionManager.class);
    private static final Gson GSON = GsonFactory.instance();

    private final ConnectionProvider connectionProvider;
    private final LocalMatchDao localMatchDao;
    private final BufferedThrowDao bufferedThrowDao;

    /**
     * Inizializza {@code MatchSessionManager} con i dati e le dipendenze indicati.
     *
     * @param connectionProvider gestore delle connessioni e delle transazioni
     * @param localMatchDao componente {@link it.uniupo.pissir.darts.edge.dao.LocalMatchDao} utilizzato dall'operazione
     * @param bufferedThrowDao componente {@link it.uniupo.pissir.darts.edge.dao.BufferedThrowDao} utilizzato dall'operazione
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public MatchSessionManager(ConnectionProvider connectionProvider, LocalMatchDao localMatchDao,
                                BufferedThrowDao bufferedThrowDao) {
        this.connectionProvider = connectionProvider;
        this.localMatchDao = localMatchDao;
        this.bufferedThrowDao = bufferedThrowDao;
    }

    /**
     * Raccoglie i dati di risultato tiro.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param localUuid identificativo univoco della partita creata offline
     * @param participantId identificativo del partecipante
     * @param nomePartecipante nome da mostrare per il partecipante
     * @param settore settore del bersaglio colpito
     * @param moltiplicatore moltiplicatore del settore
     * @param punti punti assegnati al tiro
     * @param esito esito del tiro o della partita
     * @param punteggioResiduo punteggio rimanente dopo il tiro
     * @param vittoria indicatore di vittoria
     * @param motivo motivazione associata all'esito
     * @see it.uniupo.pissir.darts.game.model.darts.DartSector
     *  
     */
    public record RisultatoTiro(String localUuid, long participantId, String nomePartecipante, DartSector settore,
                                 Multiplier moltiplicatore, int punti, Esito esito, int punteggioResiduo,
                                 boolean vittoria, String motivo) {
    }

    /**
     * Applica tiro per la partita.
     *
     * @param tiro tiro da utilizzare
     * @return il risultato della ricerca, oppure {@link java.util.Optional#empty()} se assente
     * @see java.util.Optional
     *  
     */
    public Optional<RisultatoTiro> applicaTiro(ScoreCalculatorService.TiroInterpretato tiro) {
        Optional<LocalMatch> matchOpt = localMatchDao.findInCorso();
        if (matchOpt.isEmpty()) {
            LOG.info("Tiro ricevuto senza una partita in corso: ignorato");
            return Optional.empty();
        }
        LocalMatch match = matchOpt.get();
        ScoringRule regola = parseRegola(match.scoringRuleJson());

        return connectionProvider.withTransaction(connection -> {
            List<LocalParticipant> partecipanti = localMatchDao.findParticipantsByMatch(connection, match.localUuid());
            if (partecipanti.isEmpty()) {
                LOG.warn("Partita {} senza partecipanti: tiro ignorato", match.localUuid());
                return Optional.<RisultatoTiro>empty();
            }
            List<BufferedThrow> rilevanti = bufferedThrowDao.findByMatch(connection, match.localUuid()).stream()
                    .filter(t -> t.esito() != Esito.IGNORATO)
                    .toList();
            TurnoLocale turno = calcolaTurno(partecipanti, rilevanti, regola);
            LocalParticipant attivo = partecipanti.stream().filter(p -> p.id() == turno.participantId())
                    .findFirst().orElseThrow();

            ThrowOutcome esito = DartsRuleEngine.valuta(regola, attivo.punteggioCorrente(), turno.puntiInizioTurno(),
                    turno.tiriGiaFattiNelTurno(), tiro.settore(), tiro.moltiplicatore());

            bufferedThrowDao.insert(connection, match.localUuid(), attivo.id(), tiro.settore(), tiro.moltiplicatore(),
                    tiro.punti(), turno.numeroTurno(), esito.esito(), esito.turnoAnnullato(), tiro.timestampSensore(), false);
            localMatchDao.updateParticipantScore(connection, attivo.id(), esito.punteggioResiduo());

            if (esito.vittoria()) {
                LocalMatch conclusa = new LocalMatch(match.localUuid(), match.serverId(), match.dartboardId(),
                        match.scoringRuleJson(), match.origine(), LocalMatchStatus.COMPLETATA, match.importata(),
                        match.dataInizio(), Instant.now());
                localMatchDao.update(connection, conclusa);
            }

            return Optional.of(new RisultatoTiro(match.localUuid(), attivo.id(), attivo.nomeVisualizzato(),
                    tiro.settore(), tiro.moltiplicatore(), tiro.punti(), esito.esito(), esito.punteggioResiduo(),
                    esito.vittoria(), esito.motivo()));
        });
    }

    /**
     * Chiude partita in corso per la partita. 
     */
    public void chiudiPartitaInCorso() {
        localMatchDao.findInCorso().ifPresent(match -> {
            LocalMatch conclusa = new LocalMatch(match.localUuid(), match.serverId(), match.dartboardId(),
                    match.scoringRuleJson(), match.origine(), LocalMatchStatus.COMPLETATA, match.importata(),
                    match.dataInizio(), Instant.now());
            connectionProvider.withTransaction(connection -> {
                localMatchDao.update(connection, conclusa);
                return null;
            });
        });
    }

    /**
     * Restituisce la partita locale attualmente attiva.
     *
     * @return la partita attiva, oppure un risultato vuoto se nessuna partita è in corso
     * @see java.util.Optional
     *  
     */
    public Optional<LocalMatch> partitaInCorso() {
        return localMatchDao.findInCorso();
    }

    /**
     * Raccoglie i dati di partecipante stato.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param participantId identificativo del partecipante
     * @param nome nome della risorsa
     * @param punteggioCorrente punteggio attuale del partecipante
     *  
     */
    public record PartecipanteStato(long participantId, String nome, int punteggioCorrente) {
    }

    /**
     * Raccoglie i dati di tiro stato.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param numeroTurno numero del turno
     * @param giocatore giocatore a cui si riferiscono i dati
     * @param settore settore del bersaglio colpito
     * @param moltiplicatore moltiplicatore del settore
     * @param punti punti assegnati al tiro
     * @param esito esito del tiro o della partita
     * @param timestampSensore istante registrato dal sensore
     *  
     */
    public record TiroStato(int numeroTurno, String giocatore, String settore, String moltiplicatore,
                            int punti, String esito, String timestampSensore) {
    }

    /**
     * Raccoglie i dati di stato partita.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param localUuid identificativo univoco della partita creata offline
     * @param inCorso indicatore di partita attualmente attiva
     * @param partecipanti partecipanti associati alla partita
     * @param partecipanteAttivoId identificativo del partecipante a cui spetta il turno
     * @param numeroTurno numero del turno
     * @param storicoTiri tiri giÃ  registrati per la partita
     * @see java.util.List
     *  
     */
    public record StatoPartita(String localUuid, boolean inCorso, List<PartecipanteStato> partecipanti,
                                Long partecipanteAttivoId, int numeroTurno, List<TiroStato> storicoTiri) {
    }

    /**
     * Legge lo stato corrente della partita o del componente.
     *
     * @return stato corrente
     * @see it.uniupo.pissir.darts.edge.service.MatchSessionManager.StatoPartita
     *  
     */
    public StatoPartita statoCorrente() {
        Optional<LocalMatch> matchOpt = localMatchDao.findInCorso();
        if (matchOpt.isEmpty()) {
            return new StatoPartita(null, false, List.of(), null, 0, List.of());
        }
        LocalMatch match = matchOpt.get();
        List<LocalParticipant> partecipanti = localMatchDao.findParticipantsByMatch(match.localUuid());
        List<PartecipanteStato> stati = partecipanti.stream()
                .map(p -> new PartecipanteStato(p.id(), p.nomeVisualizzato(), p.punteggioCorrente()))
                .toList();
        List<BufferedThrow> tiri = bufferedThrowDao.findByMatch(match.localUuid());
        Map<Long, String> nomi = new HashMap<>();
        for (LocalParticipant partecipante : partecipanti) {
            nomi.put(partecipante.id(), partecipante.nomeVisualizzato());
        }
        List<TiroStato> storico = tiri.stream()
                .map(t -> new TiroStato(t.numeroTurno(), nomi.getOrDefault(t.localParticipantId(), "Partecipante sconosciuto"),
                        t.settore().name(), t.moltiplicatore().name(), t.punti(), t.esito().name(),
                        t.timestampSensore().toString()))
                .toList();
        Long attivoId = null;
        int numeroTurno = 0;
        if (!partecipanti.isEmpty()) {
            ScoringRule regola = parseRegola(match.scoringRuleJson());
            List<BufferedThrow> rilevanti = tiri.stream()
                    .filter(t -> t.esito() != Esito.IGNORATO)
                    .toList();
            TurnoLocale turno = calcolaTurno(partecipanti, rilevanti, regola);
            attivoId = turno.participantId();
            numeroTurno = turno.numeroTurno();
        }
        return new StatoPartita(match.localUuid(), true, stati, attivoId, numeroTurno, storico);
    }

    /**
     * Verifica se il comando riguarda una partita non più attiva.
     *
     * @param serverMatchId identificativo della partita sul server
     * @return {@code true} se il comando riguarda una partita non più attiva; {@code false} altrimenti
     *  
     */
    public boolean comandoObsoleto(long serverMatchId) {
        return localMatchDao.findByServerId(serverMatchId)
                .map(m -> m.stato() == LocalMatchStatus.COMPLETATA)
                .orElse(false);
    }

    /**
     * Legge e converte regola dalla richiesta o dalla configurazione.
     *
     * @param json testo JSON da deserializzare
     * @return l'oggetto {@link it.uniupo.pissir.darts.game.model.ScoringRule} prodotto o recuperato
     * @see it.uniupo.pissir.darts.game.model.ScoringRule
     *  
     */
    private ScoringRule parseRegola(String json) {
        ScoringRuleSnapshot snapshot = GSON.fromJson(json, ScoringRuleSnapshot.class);
        return new ScoringRule(0, 0, snapshot.variante(), Direzione.valueOf(snapshot.direzione()),
                snapshot.punteggioIniziale(), snapshot.punteggioObiettivo(), snapshot.chiusuraDouble(),
                snapshot.bustAnnullaTurno(), snapshot.maxTiriPerTurno(), "{}");
    }

    /**
     * Raccoglie i dati di turno locale.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param participantId identificativo del partecipante
     * @param numeroTurno numero del turno
     * @param tiriGiaFattiNelTurno tiri già effettuati nel turno
     * @param puntiInizioTurno punteggio all'inizio del turno
     *  
     */
    private record TurnoLocale(long participantId, int numeroTurno, int tiriGiaFattiNelTurno, int puntiInizioTurno) {
    }

    /**
     * Calcola turno per la partita.
     *
     * @param partecipanti partecipanti associati alla partita
     * @param rilevanti insieme dei dati relativi a rilevanti
     * @param regola regola di punteggio da applicare
     * @return l'oggetto {@link it.uniupo.pissir.darts.edge.service.MatchSessionManager.TurnoLocale} prodotto o recuperato
     * @see it.uniupo.pissir.darts.edge.service.MatchSessionManager.TurnoLocale
     *  
     */
    private TurnoLocale calcolaTurno(List<LocalParticipant> partecipanti, List<BufferedThrow> rilevanti,
                                      ScoringRule regola) {
        if (rilevanti.isEmpty()) {
            LocalParticipant primo = partecipanti.get(0);
            return new TurnoLocale(primo.id(), 1, 0, primo.punteggioCorrente());
        }
        BufferedThrow ultimo = rilevanti.get(rilevanti.size() - 1);
        long tiriStessoTurno = rilevanti.stream()
                .filter(t -> t.localParticipantId() == ultimo.localParticipantId() && t.numeroTurno() == ultimo.numeroTurno())
                .count();
        boolean turnoTerminato = ultimo.esito() != Esito.VALIDO || tiriStessoTurno >= regola.maxTiriPerTurno();

        if (!turnoTerminato) {
            LocalParticipant attuale = partecipanti.stream().filter(p -> p.id() == ultimo.localParticipantId())
                    .findFirst().orElseThrow();
            int puntiApplicati = (int) rilevanti.stream()
                    .filter(t -> t.localParticipantId() == ultimo.localParticipantId()
                            && t.numeroTurno() == ultimo.numeroTurno() && t.esito() == Esito.VALIDO)
                    .mapToInt(BufferedThrow::punti).sum();
            int segno = regola.direzione() == Direzione.DECRESCENTE ? 1 : -1;
            int puntiInizioTurno = attuale.punteggioCorrente() + segno * puntiApplicati;
            return new TurnoLocale(ultimo.localParticipantId(), ultimo.numeroTurno(), (int) tiriStessoTurno, puntiInizioTurno);
        }

        int indice = -1;
        for (int i = 0; i < partecipanti.size(); i++) {
            if (partecipanti.get(i).id() == ultimo.localParticipantId()) {
                indice = i;
                break;
            }
        }
        LocalParticipant prossimo = partecipanti.get(indice == -1 ? 0 : (indice + 1) % partecipanti.size());
        int turniPrecedenti = (int) rilevanti.stream()
                .filter(t -> t.localParticipantId() == prossimo.id())
                .mapToInt(BufferedThrow::numeroTurno)
                .distinct().count();
        return new TurnoLocale(prossimo.id(), turniPrecedenti + 1, 0, prossimo.punteggioCorrente());
    }
}
