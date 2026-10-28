package org.kevinkib.cardgames.blackjack.domain.event;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The bets are in and the cards are dealt (the hole card stays down); bets are now public. */
public record HandDealt(int handNumber, PlayerId button, Map<PlayerId, Integer> bets, Card upCard,
                        Map<PlayerId, List<Card>> seatCards) implements BlackjackEvent {

    public HandDealt {
        bets = Collections.unmodifiableMap(new LinkedHashMap<>(bets));
        Map<PlayerId, List<Card>> copy = new LinkedHashMap<>();
        seatCards.forEach((seat, cards) -> copy.put(seat, List.copyOf(cards)));
        seatCards = Collections.unmodifiableMap(copy);
    }
}
