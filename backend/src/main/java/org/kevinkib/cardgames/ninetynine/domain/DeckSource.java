package org.kevinkib.cardgames.ninetynine.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cards.domain.Card;

import java.util.List;

/**
 * The determinism seam of 99: every random choice of a game comes from here, so tests can script it.
 */
public interface DeckSource {

    /** A complete shuffled deck of 52 distinct cards for the given round; the first card is dealt first. */
    List<Card> shuffledDeck(GameId id, int round);

    /** The cards of the pile (the top card excluded) in their new stock order, when the stock runs out. */
    List<Card> reshuffle(GameId id, List<Card> cards);

    /** The index (0 to {@code seats - 1}) of the seat that starts round 1. */
    int firstSeat(GameId id, int seats);
}
