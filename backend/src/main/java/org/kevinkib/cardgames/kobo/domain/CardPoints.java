package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.Color;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

/** Kobo point values: Ace 1, 2-10 face value, Jack 11, Queen 12, black King 13, red King 0. */
public final class CardPoints {

    private CardPoints() {
    }

    public static int of(Card card) {
        if (!(card.getRank() instanceof FrenchRank rank) || !(card.getSuit() instanceof FrenchSuit suit)
                || rank == FrenchRank.JOKER) {
            throw new IllegalArgumentException("Not a Kobo card: " + card);
        }
        return switch (rank) {
            case ACE -> 1;
            case TWO -> 2;
            case THREE -> 3;
            case FOUR -> 4;
            case FIVE -> 5;
            case SIX -> 6;
            case SEVEN -> 7;
            case EIGHT -> 8;
            case NINE -> 9;
            case TEN -> 10;
            case JACK -> 11;
            case QUEEN -> 12;
            case KING -> suit.getColor() == Color.RED ? 0 : 13;
            default -> throw new IllegalArgumentException("Not a Kobo card: " + card);
        };
    }
}
