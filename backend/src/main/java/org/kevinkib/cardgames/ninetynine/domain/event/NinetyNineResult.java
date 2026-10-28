package org.kevinkib.cardgames.ninetynine.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

import java.util.Map;

/** The final places per seat (1 is the best; a place may be shared in quick mode) and why the game ended. */
public record NinetyNineResult(Map<PlayerId, Integer> places, Reason reason) {

    public enum Reason { LAST_STANDING, QUICK, FORFEIT }

    public NinetyNineResult {
        places = Map.copyOf(places);
    }
}
