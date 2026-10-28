package org.kevinkib.cardgames.blackjack.domain;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The hand that was just settled, kept on the table during the next betting: the dealer's cards, each seat's hands
 * with their net, the net per seat and the best hand(s).
 */
public record LastHand(int handNumber, List<Card> dealerCards, Map<PlayerId, List<SettledHand>> seats,
                       Map<PlayerId, Integer> nets, List<PlayerId> bestSeats) {

    /** One settled hand and what it won (positive) or lost (negative). */
    public record SettledHand(PlayerHand hand, int net) {
    }

    public LastHand {
        dealerCards = List.copyOf(dealerCards);
        Map<PlayerId, List<SettledHand>> copy = new LinkedHashMap<>();
        seats.forEach((seat, hands) -> copy.put(seat, List.copyOf(hands)));
        seats = Collections.unmodifiableMap(copy);
        nets = Collections.unmodifiableMap(new LinkedHashMap<>(nets));
        bestSeats = List.copyOf(bestSeats);
    }
}
