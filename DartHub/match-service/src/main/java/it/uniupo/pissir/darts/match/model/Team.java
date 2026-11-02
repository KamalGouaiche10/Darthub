package it.uniupo.pissir.darts.match.model;

/**
 * Riga della tabella teams.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo della squadra
 * @param nome nome della risorsa
 *  
 */
public record Team(long id, String nome) {
}
