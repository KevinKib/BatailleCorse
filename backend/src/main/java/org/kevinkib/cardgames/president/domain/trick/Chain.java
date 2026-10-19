package org.kevinkib.cardgames.president.domain.trick;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;

/**
 * The plays of one rank at the top of a trick, made in succession: the rank, the cards played, and how many plays
 * made them. A chain of two plays or more, or a lone card, is what opens the completion window.
 */
public record Chain(FrenchRank rank, List<Card> cards, int plays) {

    public Chain {
        cards = List.copyOf(cards);
    }
}
