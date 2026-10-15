package org.kevinkib.cardgames.president.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cards.domain.Card;

import java.util.List;

/**
 * Where the 52 cards of a President round come from. The returned list is in deal order: the first card goes
 * to the seat after the dealer, the next to the following seat, and so on until the deck is empty.
 */
public interface DeckSource {

    /** A complete deck of 52 distinct cards for the given round; {@code seed} drives any shuffling. */
    List<Card> deck(GameId id, int round, long seed);
}
