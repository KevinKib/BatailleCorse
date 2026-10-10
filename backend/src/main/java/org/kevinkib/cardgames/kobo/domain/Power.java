package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

/** The optional power of a card discarded directly from the draw. */
public enum Power {
    NONE, PEEK_OWN, PEEK_OTHER, BLIND_SWAP, KING;

    public static Power of(Card card) {
        if (!(card.getRank() instanceof FrenchRank rank)) {
            throw new IllegalArgumentException("Not a French card: " + card);
        }
        return switch (rank) {
            case SEVEN, EIGHT -> PEEK_OWN;
            case NINE, TEN -> PEEK_OTHER;
            case JACK, QUEEN -> BLIND_SWAP;
            case KING -> KING;
            default -> NONE;
        };
    }
}
