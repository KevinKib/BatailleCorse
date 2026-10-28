package org.kevinkib.cardgames.ninetynine.domain;

import org.kevinkib.cards.domain.Card;

import java.util.List;

/**
 * The effect of each card on the running total (spec section 3.2), pure. Shared by the aggregate, the DTO hints and
 * the bots, so the client never computes a rule. P0 fixes the signatures only; the bodies come with the domain chunk
 * (C1), which must not change a signature.
 */
public final class CardEffect {

    /** The highest total a seat may bring to the table without going over. */
    public static final int LIMIT = 99;

    private CardEffect() {
    }

    /** The values an Ace may take at this total: 1 always, 11 only when it keeps the total at 99 or below. */
    public static List<Integer> aceValues(int total) {
        throw new UnsupportedOperationException("Filled by the domain chunk");
    }

    /** Whether at least one value of the card keeps the total at {@link #LIMIT} or below. */
    public static boolean isSafe(int total, Card card) {
        throw new UnsupportedOperationException("Filled by the domain chunk");
    }

    /** The cards of the hand that are safe at this total, in hand order. */
    public static List<Card> safeCards(int total, List<Card> hand) {
        throw new UnsupportedOperationException("Filled by the domain chunk");
    }

    /**
     * The total after the card; {@code aceValue} (1 or 11) is read for an Ace only.
     *
     * @throws IllegalArgumentException for an Ace without a valid value
     */
    public static int apply(int total, Card card, Integer aceValue) {
        throw new UnsupportedOperationException("Filled by the domain chunk");
    }

    /** Whether the card reverses the direction of play (the 4). */
    public static boolean reverses(Card card) {
        throw new UnsupportedOperationException("Filled by the domain chunk");
    }
}
