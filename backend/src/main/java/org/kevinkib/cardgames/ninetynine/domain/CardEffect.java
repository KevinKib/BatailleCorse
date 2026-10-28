package org.kevinkib.cardgames.ninetynine.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;

/**
 * The effect of each card on the running total (spec section 3.2), pure. Shared by the aggregate, the DTO hints and
 * the bots, so the client never computes a rule. The signatures were fixed by P0; the 5 counts +5, the 10 stops at 0, the K sets 99.
 */
public final class CardEffect {

    /** The highest total a seat may bring to the table without going over. */
    public static final int LIMIT = 99;

    private CardEffect() {
    }

    /** The values an Ace may take at this total: 1 always, 11 only when it keeps the total at 99 or below. */
    public static List<Integer> aceValues(int total) {
        if (total + 11 <= LIMIT) {
            return List.of(1, 11);
        }
        return total + 1 <= LIMIT ? List.of(1) : List.of();
    }

    /** Whether at least one value of the card keeps the total at {@link #LIMIT} or below. */
    public static boolean isSafe(int total, Card card) {
        if (rank(card) == FrenchRank.ACE) {
            return !aceValues(total).isEmpty();
        }
        return apply(total, card, null) <= LIMIT;
    }

    /** The cards of the hand that are safe at this total, in hand order. */
    public static List<Card> safeCards(int total, List<Card> hand) {
        return hand.stream().filter(card -> isSafe(total, card)).toList();
    }

    /**
     * The total after the card; {@code aceValue} (1 or 11) is read for an Ace only.
     *
     * @throws IllegalArgumentException for an Ace without a valid value
     */
    public static int apply(int total, Card card, Integer aceValue) {
        FrenchRank rank = rank(card);
        return switch (rank) {
            case ACE -> {
                if (aceValue == null || (aceValue != 1 && aceValue != 11)) {
                    throw new IllegalArgumentException("An Ace counts 1 or 11, not " + aceValue);
                }
                yield total + aceValue;
            }
            case TWO -> total + 2;
            case THREE -> total + 3;
            case FIVE -> total + 5;
            case SIX -> total + 6;
            case SEVEN -> total + 7;
            case EIGHT -> total + 8;
            case FOUR, NINE -> total;
            case TEN -> Math.max(0, total - 10);
            case JACK, QUEEN -> total + 10;
            case KING -> LIMIT;
            default -> throw new IllegalArgumentException("Not a 99 card: " + card);
        };
    }

    /** Whether the card reverses the direction of play (the 4). */
    public static boolean reverses(Card card) {
        return rank(card) == FrenchRank.FOUR;
    }

    private static FrenchRank rank(Card card) {
        return (FrenchRank) card.getRank();
    }
}
