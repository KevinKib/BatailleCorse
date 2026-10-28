package org.kevinkib.cardgames.ninetynine.domain.event;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.List;

/**
 * A stuck seat (no safe card) played a card that took the total over 99 and lost a life. {@code totalBefore} is the
 * total over 99 after that card (the name predates the forced play); {@code hand} is what the seat still held.
 */
public record Busted(PlayerId seat, int totalBefore, List<Card> hand, int livesLeft) implements NinetyNineEvent {

    public Busted {
        hand = List.copyOf(hand);
    }
}
