package org.kevinkib.cardgames.blackjack.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cards.domain.Card;

import java.util.List;

/**
 * The determinism seam of Blackjack: every shuffle comes from here, so tests can script it.
 */
public interface ShoeSource {

    /** A shuffled shoe of 6 decks (312 cards) for the given hand; the first card is dealt first. */
    List<Card> shuffledShoe(GameId id, int handNumber);
}
