package org.kevinkib.cardgames.kilodemerde.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cards.domain.Card;

import java.util.List;

/**
 * The determinism seam of Kilo de merde: every random choice of a game comes from here, so tests can script it.
 */
public interface DeckSource {

    /** The hand deck in its dealing order; the first four cards go to the first seat, and so on. */
    List<Card> shuffleHandDeck(List<Card> handDeck);

    /** The kilo pile in its new order; the first card is the top. */
    List<Card> shufflePile(List<Card> pile);

    /** Same as {@link #shuffleHandDeck(List)} for one game; a scripted source may fix the order per game. */
    default List<Card> shuffleHandDeck(GameId id, List<Card> handDeck) {
        return shuffleHandDeck(handDeck);
    }

    /** Same as {@link #shufflePile(List)} for one game; a scripted source may fix the order per game. */
    default List<Card> shufflePile(GameId id, List<Card> pile) {
        return shufflePile(pile);
    }
}
