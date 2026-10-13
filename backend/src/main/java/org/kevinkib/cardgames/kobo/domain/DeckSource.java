package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cards.domain.Card;

import java.util.List;

/**
 * Where the 52 cards of a Kobo round come from. The returned list is in deal order: the first four cards
 * go to seat 0, the next four to seat 1 and so on, the remainder being the draw pile from its top.
 */
public interface DeckSource {

    /** A complete deck of 52 distinct cards for the given round; {@code seed} drives any shuffling. */
    List<Card> deck(GameId id, int round, long seed);
}
