package org.kevinkib.cardgames.kilodemerde.domain;

import org.kevinkib.cardgames.game.PlayerId;

import java.util.Map;

/** The final places per seat (1 is the best; a place may be shared, A13), the kilos per seat, and why it ended. */
public record KiloDeMerdeResult(Map<PlayerId, Integer> places, Map<PlayerId, Integer> kilos, Reason reason) {

    public enum Reason { ROUNDS_PLAYED, PILE_EMPTY, FORFEIT }

    public KiloDeMerdeResult {
        places = Map.copyOf(places);
        kilos = Map.copyOf(kilos);
    }
}
