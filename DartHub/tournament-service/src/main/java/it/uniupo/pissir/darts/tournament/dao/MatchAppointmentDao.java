package it.uniupo.pissir.darts.tournament.dao;

import it.uniupo.pissir.darts.common.exception.ConflictException;
import it.uniupo.pissir.darts.common.exception.NotFoundException;
import it.uniupo.pissir.darts.common.persistence.ConnectionProvider;
import it.uniupo.pissir.darts.common.persistence.SqlExceptionTranslator;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Legge e modifica appuntamenti delle partite nel database del servizio.
 *
 * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
 *  
 */
public final class MatchAppointmentDao {
    /**
     * Raccoglie i dati di acceptance.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param home partecipante di casa
     * @param away partecipante ospite
     *  
     */
    public record Acceptance(boolean home, boolean away) {
        /**
         * Verifica che entrambi i partecipanti soddisfino la condizione.
         *
         * @return l'esito della condizione verificata
         *  
         */
        public boolean both() { return home && away; }
    }
    /**
     * Raccoglie i dati di appuntamento.
     * <p>
     * I componenti sono esposti attraverso gli accessor del record.
     *
     * @param matchId identificativo della partita
     * @param tournamentId identificativo del torneo
     * @param dartboardId identificativo del bersaglio
     * @param scheduledAt istante programmato per l'inizio della partita
     * @param durationMinutes durata della partita in minuti
     * @param homeReadyAt istante di conferma della presenza del partecipante di casa
     * @param awayReadyAt istante di conferma della presenza del partecipante ospite
     * @param outcome esito della partita
     * @param revision versione dell'appuntamento
     * @see java.time.Instant
     *  
     */
    public record Appointment(long matchId, long tournamentId, long dartboardId, Instant scheduledAt,
                              int durationMinutes, Instant homeReadyAt, Instant awayReadyAt, String outcome, int revision) {
        /**
         * Restituisce il scheduled at da utilizzare.
         *
         * @return l'oggetto {@link java.time.Instant} prodotto o recuperato
         * @see java.time.Instant
         *  
         */
        public Instant linkAt() { return scheduledAt; }
        /**
         * Calcola l'istante limite dell'appuntamento.
         *
         * @param graceMinutes ritardo ammesso in minuti
         * @return l'oggetto {@link java.time.Instant} prodotto o recuperato
         * @see java.time.Instant
         *  
         */
        public Instant deadline(int graceMinutes) { return scheduledAt.plusSeconds(graceMinutes * 60L); }
    }

    private final ConnectionProvider db;
    /**
     * Inizializza {@code MatchAppointmentDao} con i dati e le dipendenze indicati.
     *
     * @param db db da utilizzare
     * @see it.uniupo.pissir.darts.common.persistence.ConnectionProvider
     *  
     */
    public MatchAppointmentDao(ConnectionProvider db) { this.db = db; }

    /**
     * Cerca l'appuntamento della partita usando i dati della richiesta.
     *
     * @param matchId identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment
     *  
     */
    public Appointment find(long matchId) {
        return db.withConnection(c -> find(c, matchId));
    }

    /**
     * Cerca l'appuntamento della partita usando i dati della richiesta.
     *
     * @param matchId identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment
     *  
     */
    public Appointment findOrNull(long matchId) {
        try { return find(matchId); }
        catch (NotFoundException ignored) { return null; }
    }

    /**
     * Registra l'accettazione dell'appuntamento da parte del giocatore.
     *
     * @param matchId identificativo della partita
     * @param home partecipante di casa
     * @param now istante corrente usato nei controlli
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Acceptance} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Acceptance
     *  
     */
    public Acceptance accept(long matchId, boolean home, Instant now) {
        return db.withTransaction(c -> {
            try (PreparedStatement insert = c.prepareStatement("INSERT OR IGNORE INTO match_acceptances(match_id) VALUES(?)");
                 PreparedStatement update = c.prepareStatement("UPDATE match_acceptances SET "
                         + (home ? "home_accepted_at" : "away_accepted_at") + "=? WHERE match_id=? AND "
                         + (home ? "home_accepted_at" : "away_accepted_at") + " IS NULL");
                 PreparedStatement read = c.prepareStatement("SELECT home_accepted_at,away_accepted_at FROM match_acceptances WHERE match_id=?")) {
                insert.setLong(1, matchId); insert.executeUpdate();
                update.setString(1, now.toString()); update.setLong(2, matchId);
                if (update.executeUpdate() == 1) {
                    try (PreparedStatement notices = c.prepareStatement("DELETE FROM pairing_notice_reads WHERE match_id=?")) {
                        notices.setLong(1, matchId); notices.executeUpdate();
                    }
                }
                read.setLong(1, matchId);
                try (ResultSet rs = read.executeQuery()) {
                    rs.next();
                    return new Acceptance(rs.getString(1) != null, rs.getString(2) != null);
                }
            } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
        });
    }

    /**
     * Compone lo stato di accettazione dell'appuntamento.
     *
     * @param matchId identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Acceptance} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Acceptance
     *  
     */
    public Acceptance acceptance(long matchId) {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT home_accepted_at,away_accepted_at FROM match_acceptances WHERE match_id=?")) {
                ps.setLong(1, matchId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? new Acceptance(rs.getString(1) != null, rs.getString(2) != null)
                            : new Acceptance(false, false);
                }
            } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
        });
    }

    /**
     * Prenota il primo intervallo libero fra quelli consentiti.
     *
     * @param tournamentId identificativo del torneo
     * @param matchId identificativo della partita
     * @param dartboardId identificativo del bersaglio
     * @param earliestStart primo istante di inizio consentito
     * @param latestStart ultimo istante di inizio consentito
     * @param durationMinutes durata della partita in minuti
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment} prodotto o recuperato
     * @throws ConflictException se viene rilevato l'errore «Durata non valida»
     * @see it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment
     *  
     */
    public Appointment scheduleFirstAvailable(long tournamentId, long matchId, long dartboardId,
                                              Instant earliestStart, Instant latestStart, int durationMinutes) {
        if (durationMinutes < 1 || durationMinutes > 1440)
            throw new ConflictException("DURATA_NON_VALIDA", "Durata non valida");
        return db.withTransaction(c -> {
            try { return find(c, matchId); }
            catch (NotFoundException ignored) {   }

            List<Appointment> occupied = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM match_appointments WHERE dartboard_id=? "
                    + "AND match_id<>? AND (outcome IS NULL OR outcome='IN_CORSO')")) {
                ps.setLong(1, dartboardId); ps.setLong(2, matchId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) occupied.add(map(rs));
                }
            } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
            occupied.sort(Comparator.comparing(Appointment::scheduledAt));

            Instant candidate = earliestStart;
            long durationSeconds = durationMinutes * 60L;
            for (Appointment booked : occupied) {
                Instant bookedEnd = booked.scheduledAt().plusSeconds(booked.durationMinutes() * 60L);
                if (!bookedEnd.isAfter(candidate)) continue;
                if (!candidate.plusSeconds(durationSeconds).isAfter(booked.scheduledAt())) break;
                candidate = bookedEnd;
                if (candidate.isAfter(latestStart)) break;
            }
            if (candidate.isAfter(latestStart))
                throw new ConflictException("INTERVALLO_ESAURITO", "Non resta un'ora libera su questo bersaglio prima della fine del torneo");

            try (PreparedStatement ps = c.prepareStatement("INSERT INTO match_appointments"
                    + "(match_id,tournament_id,dartboard_id,scheduled_at,duration_minutes) VALUES(?,?,?,?,?)")) {
                ps.setLong(1, matchId); ps.setLong(2, tournamentId); ps.setLong(3, dartboardId);
                ps.setString(4, candidate.toString()); ps.setInt(5, durationMinutes);
                ps.executeUpdate();
            } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
            try (PreparedStatement notices = c.prepareStatement("DELETE FROM pairing_notice_reads WHERE match_id=?")) {
                notices.setLong(1, matchId); notices.executeUpdate();
            } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
            return find(c, matchId);
        });
    }

    /**
     * Cerca l'appuntamento della partita usando i dati della richiesta.
     *
     * @param c carattere da elaborare
     * @param matchId identificativo della partita
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment
     *  
     */
    private Appointment find(Connection c, long matchId) {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM match_appointments WHERE match_id=?")) {
            ps.setLong(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NotFoundException("Orario partita non impostato");
                return map(rs);
            }
        } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
    }

    /**
     * Verifica se l'appuntamento ha raggiunto la scadenza.
     *
     * @param now istante corrente usato nei controlli
     * @param graceMinutes ritardo ammesso in minuti
     * @return gli elementi trovati o prodotti dall'operazione
     * @see java.util.List
     *  
     */
    public List<Appointment> due(Instant now, int graceMinutes) {
        return db.withConnection(c -> {
            List<Appointment> found = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT a.* FROM match_appointments a WHERE "
                    + "(a.outcome IS NULL OR a.outcome IN ('IN_CORSO','RISOLUZIONE') OR "
                    + "(a.outcome IN ('VITTORIA_A_TAVOLINO','DOPPIA_ASSENZA','COMPLETATA') AND NOT EXISTS "
                    + "(SELECT 1 FROM processed_matches p WHERE p.match_id=a.match_id))) AND unixepoch(a.scheduled_at) <= unixepoch(?)")) {
                ps.setString(1, now.toString());
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) found.add(map(rs)); }
                return found;
            } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
        });
    }

    /**
     * Pianifica l'appuntamento della partita.
     *
     * @param tournamentId identificativo del torneo
     * @param matchId identificativo della partita
     * @param dartboardId identificativo del bersaglio
     * @param at istante dell'operazione
     * @param durationMinutes durata della partita in minuti
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment} prodotto o recuperato
     * @throws ConflictException se viene rilevato l'errore «Durata non valida»
     * @see it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment
     *  
     */
    public Appointment schedule(long tournamentId, long matchId, long dartboardId, Instant at, int durationMinutes) {
        if (durationMinutes < 1 || durationMinutes > 1440) throw new ConflictException("DURATA_NON_VALIDA", "Durata non valida");
        return db.withTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT match_id FROM match_appointments WHERE dartboard_id=? AND match_id<>? "
                    + "AND (outcome IS NULL OR outcome='IN_CORSO') AND unixepoch(scheduled_at) < unixepoch(?) "
                    + "AND unixepoch(scheduled_at) + duration_minutes * 60 > unixepoch(?) LIMIT 1")) {
                ps.setLong(1, dartboardId); ps.setLong(2, matchId);
                ps.setString(3, at.plusSeconds(durationMinutes * 60L).toString()); ps.setString(4, at.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) throw new ConflictException("BERSAGLIO_OCCUPATO", "Bersaglio già prenotato nell'orario richiesto");
                }
            } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO match_appointments(match_id,tournament_id,dartboard_id,scheduled_at,duration_minutes) "
                    + "VALUES(?,?,?,?,?) ON CONFLICT(match_id) DO UPDATE SET scheduled_at=excluded.scheduled_at, "
                    + "duration_minutes=excluded.duration_minutes, home_ready_at=NULL, away_ready_at=NULL, revision=revision+1 "
                    + "WHERE outcome IS NULL")) {
                ps.setLong(1, matchId); ps.setLong(2, tournamentId); ps.setLong(3, dartboardId);
                ps.setString(4, at.toString()); ps.setInt(5, durationMinutes);
                if (ps.executeUpdate() == 0) throw new ConflictException("PARTITA_CONCLUSA", "La partita è già conclusa");
            } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM pairing_notice_reads WHERE match_id=?")) {
                ps.setLong(1, matchId); ps.executeUpdate();
            } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
            return find(c, matchId);
        });
    }

    /**
     * Registra la presenza del giocatore all'appuntamento.
     *
     * @param matchId identificativo della partita
     * @param home partecipante di casa
     * @param now istante corrente usato nei controlli
     * @param graceMinutes ritardo ammesso in minuti
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment
     *  
     */
    public Appointment ready(long matchId, boolean home, Instant now, int graceMinutes) {
        return db.withTransaction(c -> {
            Appointment a = find(c, matchId);
            if (a.outcome() != null || now.isBefore(a.scheduledAt()) || now.isAfter(a.deadline(graceMinutes)))
                throw new ConflictException("PRESENZA_SCADUTA", "Il termine per presentarsi è scaduto");
            try (PreparedStatement ps = c.prepareStatement("UPDATE match_appointments SET "
                    + (home ? "home_ready_at" : "away_ready_at") + "=COALESCE(" + (home ? "home_ready_at" : "away_ready_at")
                    + ",?) WHERE match_id=?")) {
                ps.setString(1, now.toString()); ps.setLong(2, matchId); ps.executeUpdate();
            } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
            return find(c, matchId);
        });
    }

    /**
     * Imposta esito per il componente.
     *
     * @param matchId identificativo della partita
     * @param outcome esito della partita
     * @param revision versione dell'appuntamento
     * @return l'esito della condizione verificata
     *  
     */
    public boolean setOutcome(long matchId, String outcome, int revision) {
        return db.withTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE match_appointments SET outcome=? WHERE match_id=? AND revision=? "
                    + "AND (outcome IS NULL OR outcome IN ('IN_CORSO','RISOLUZIONE'))")) {
                ps.setString(1, outcome); ps.setLong(2, matchId); ps.setInt(3, revision);
                boolean changed = ps.executeUpdate() == 1;
                if (changed && !"IN_CORSO".equals(outcome)) {
                    try (PreparedStatement notices = c.prepareStatement(
                            "DELETE FROM pairing_notice_reads WHERE match_id=?")) {
                        notices.setLong(1, matchId); notices.executeUpdate();
                    }
                }
                return changed;
            } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
        });
    }

    /**
     * Registra la richiesta di risoluzione per assenza.
     *
     * @param matchId identificativo della partita
     * @param now istante corrente usato nei controlli
     * @param graceMinutes ritardo ammesso in minuti
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment} prodotto o recuperato
     * @see it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment
     *  
     */
    public Appointment claimAbsence(long matchId, Instant now, int graceMinutes) {
        return db.withTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE match_appointments SET outcome='RISOLUZIONE' "
                    + "WHERE match_id=? AND outcome IS NULL AND unixepoch(scheduled_at)+? <= unixepoch(?) "
                    + "AND (home_ready_at IS NULL OR away_ready_at IS NULL)")) {
                ps.setLong(1, matchId); ps.setLong(2, graceMinutes * 60L); ps.setString(3, now.toString());
                return ps.executeUpdate() == 1 ? find(c, matchId) : null;
            } catch (SQLException e) { throw SqlExceptionTranslator.translate(e); }
        });
    }

    /**
     * Converte i dati ricevuti nel tipo richiesto.
     *
     * @param rs riga corrente del risultato JDBC
     * @return l'oggetto {@link it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment} prodotto o recuperato
     * @throws SQLException se la lettura o la modifica dei dati JDBC non riesce
     * @see it.uniupo.pissir.darts.tournament.dao.MatchAppointmentDao.Appointment
     *  
     */
    private static Appointment map(ResultSet rs) throws SQLException {
        return new Appointment(rs.getLong("match_id"), rs.getLong("tournament_id"), rs.getLong("dartboard_id"),
                Instant.parse(rs.getString("scheduled_at")), rs.getInt("duration_minutes"),
                instant(rs.getString("home_ready_at")), instant(rs.getString("away_ready_at")), rs.getString("outcome"), rs.getInt("revision"));
    }
    /**
     * Converte il valore in un istante.
     *
     * @param value testo da elaborare
     * @return l'oggetto {@link java.time.Instant} prodotto o recuperato
     * @see java.time.Instant
     *  
     */
    private static Instant instant(String value) { return value == null ? null : Instant.parse(value); }
}
