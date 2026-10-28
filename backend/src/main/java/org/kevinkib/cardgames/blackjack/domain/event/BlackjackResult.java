package org.kevinkib.cardgames.blackjack.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The final places per seat (1 is the best; equal stacks share a place; empty in solo), the final stacks and why
 * the game ended.
 */
public record BlackjackResult(Map<PlayerId, Integer> places, Map<PlayerId, Integer> stacks, Reason reason) {

    /** {@code BROKE}: the only player of a solo game reached 0. */
    public enum Reason { HANDS_PLAYED, LAST_STANDING, BROKE, FORFEIT }

    public BlackjackResult {
        places = Collections.unmodifiableMap(new LinkedHashMap<>(places));
        stacks = Collections.unmodifiableMap(new LinkedHashMap<>(stacks));
    }
}
