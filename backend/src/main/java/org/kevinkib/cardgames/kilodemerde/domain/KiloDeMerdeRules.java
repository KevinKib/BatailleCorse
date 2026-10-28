package org.kevinkib.cardgames.kilodemerde.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Pure rules shared by the aggregate, the pass timer and the bots, so no rule is written twice (spec section 8.1).
 */
public final class KiloDeMerdeRules {

    /** Cards in a hand. */
    public static final int HAND_SIZE = 4;

    /** Pass steps without a slap after which a round stalls (A10). */
    public static final int STALL_LIMIT = 40;

    /** Tie-break suit order of {@link #defaultPass}: clubs, diamonds, hearts, spades. */
    private static final List<FrenchSuit> SUIT_ORDER = List.of(
            FrenchSuit.CLUB, FrenchSuit.DIAMOND, FrenchSuit.HEART, FrenchSuit.SPADE);

    private KiloDeMerdeRules() {
    }

    /** Whether the hand is four cards of one rank. */
    public static boolean isFourOfAKind(List<Card> hand) {
        return hand.size() == HAND_SIZE && hand.stream().allMatch(card -> card.isSameRankAs(hand.get(0)));
    }

    /** Whether any of the hands is a four of a kind (a slap is valid then, A4). */
    public static boolean anyFourOfAKind(Collection<List<Card>> hands) {
        return hands.stream().anyMatch(KiloDeMerdeRules::isFourOfAKind);
    }

    /**
     * The card passed for a seat that did not choose (A15): a card of the rank the hand holds fewest of; ties broken
     * by rank order 2..K (the Ace last), then by suit order clubs, diamonds, hearts, spades.
     *
     * @throws IllegalArgumentException on an empty hand
     */
    public static Card defaultPass(List<Card> hand) {
        if (hand.isEmpty()) {
            throw new IllegalArgumentException("An empty hand has nothing to pass");
        }
        Map<FrenchRank, Integer> counts = new EnumMap<>(FrenchRank.class);
        for (Card card : hand) {
            counts.merge((FrenchRank) card.getRank(), 1, Integer::sum);
        }
        return hand.stream()
                .min(Comparator
                        .comparingInt((Card card) -> counts.get((FrenchRank) card.getRank()))
                        .thenComparingInt(card -> ((FrenchRank) card.getRank()).ordinal())
                        .thenComparingInt(card -> SUIT_ORDER.indexOf((FrenchSuit) card.getSuit())))
                .orElseThrow();
    }
}
