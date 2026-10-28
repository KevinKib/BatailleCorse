package org.kevinkib.cardgames.kilodemerde.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.Collection;

/**
 * What a kilo card counts (spec section 3.4): Ace 100, King 13, Queen 12, Jack 11, the others their face value.
 * The 7 is the flush and counts 0.
 */
public final class KiloValue {

    private KiloValue() {
    }

    public static int of(Card card) {
        FrenchRank rank = (FrenchRank) card.getRank();
        return switch (rank) {
            case ACE -> 100;
            case KING -> 13;
            case QUEEN -> 12;
            case JACK -> 11;
            case TEN -> 10;
            case NINE -> 9;
            case EIGHT -> 8;
            case SEVEN -> 0;
            case SIX -> 6;
            case FIVE -> 5;
            case FOUR -> 4;
            case THREE -> 3;
            case TWO -> 2;
            case JOKER -> throw new IllegalArgumentException("A joker is never a kilo card");
        };
    }

    /** Whether drawing this card flushes the drawer's kilos back into the pile. */
    public static boolean isFlush(Card card) {
        return card.getRank() == FrenchRank.SEVEN;
    }

    public static int total(Collection<Card> cards) {
        return cards.stream().mapToInt(KiloValue::of).sum();
    }
}
