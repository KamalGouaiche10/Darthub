package it.uniupo.pissir.darts.match.dto;

import java.util.List;

/**
 * Rappresentazione pubblica di una squadra con i suoi membri.
 * <p>
 * I componenti sono esposti attraverso gli accessor del record.
 *
 * @param id identificativo della squadra
 * @param nome nome della risorsa
 * @param membri giocatori appartenenti alla squadra
 * @see java.util.List
 *  
 */
public record TeamDTO(long id, String nome, List<Long> membri) {
}
