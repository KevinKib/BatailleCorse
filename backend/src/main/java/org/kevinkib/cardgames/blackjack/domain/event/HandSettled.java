package org.kevinkib.cardgames.blackjack.domain.event;

import org.kevinkib.cardgames.game.PlayerId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The hand is settled: the net gain of each seat, the stacks after the settlement and the best hand(s) of the hand
 * (largest positive net, information only; empty when nobody gained).
 */
public record HandSettled(int handNumber, Map<PlayerId, Integer> nets, Map<PlayerId, Integer> stacks,
                          List<PlayerId> bestSeats) implements BlackjackEvent {

    public HandSettled {
        nets = Collections.unmodifiableMap(new LinkedHashMap<>(nets));
        stacks = Collections.unmodifiableMap(new LinkedHashMap<>(stacks));
        bestSeats = List.copyOf(bestSeats);
    }
}
