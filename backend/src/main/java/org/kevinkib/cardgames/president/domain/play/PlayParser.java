package org.kevinkib.cardgames.president.domain.play;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;

/** Turns raw cards into a {@link Play}, or refuses them. */
public final class PlayParser {

    private PlayParser() {
    }

    public static Play parse(List<Card> cards) throws IllegalPlayException {
        if (cards == null || cards.isEmpty()) {
            throw new IllegalPlayException("A play holds at least one card");
        }
        FrenchRank rank = null;
        for (Card card : cards) {
            if (card == null || !(card.getRank() instanceof FrenchRank r)) {
                throw new IllegalPlayException("Unknown card");
            }
            if (r == FrenchRank.JOKER) {
                throw new IllegalPlayException("Jokers are not in play");
            }
            if (rank == null) {
                rank = r;
            } else if (rank != r) {
                throw new IllegalPlayException("A play holds cards of a single rank");
            }
        }
        return new Play(cards, rank);
    }
}
