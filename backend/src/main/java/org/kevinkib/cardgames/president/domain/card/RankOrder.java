package org.kevinkib.cardgames.president.domain.card;

import org.kevinkib.cards.domain.Rank;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;

/**
 * The strength of a rank. P1 has the standard order only (3 low to 2 high, jokers excluded); the
 * {@code ranking} option and the revolution state arrive as other factories of this class (V2), so that
 * callers keep asking "is this stronger" without knowing which order is in force.
 */
public final class RankOrder {

    private static final List<FrenchRank> STANDARD = List.of(
            FrenchRank.THREE, FrenchRank.FOUR, FrenchRank.FIVE, FrenchRank.SIX, FrenchRank.SEVEN,
            FrenchRank.EIGHT, FrenchRank.NINE, FrenchRank.TEN, FrenchRank.JACK, FrenchRank.QUEEN,
            FrenchRank.KING, FrenchRank.ACE, FrenchRank.TWO);

    private final List<FrenchRank> ascending;

    private RankOrder(List<FrenchRank> ascending) {
        this.ascending = ascending;
    }

    public static RankOrder standard() {
        return new RankOrder(STANDARD);
    }

    /** The position of the rank in the order, 0 for the weakest. */
    public int strength(Rank rank) {
        int index = ascending.indexOf(rank);
        if (index < 0) {
            throw new IllegalArgumentException("No place in the ranking for " + rank);
        }
        return index;
    }

    /** Negative when {@code a} is weaker than {@code b}, zero when equal, positive when stronger. */
    public int compare(Rank a, Rank b) {
        return Integer.compare(strength(a), strength(b));
    }

    /** The ranks from the weakest to the strongest. */
    public List<FrenchRank> ranksAscending() {
        return ascending;
    }
}
