package org.kevinkib.cardgames.president.domain.exchange;

import org.kevinkib.cardgames.president.domain.card.HandSort;
import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cards.domain.Card;

import java.util.List;

/** Picks cards by strength for the exchange. Cards of a tied rank are interchangeable; the suit order breaks ties. */
public final class BestCards {

    private BestCards() {
    }

    /** The {@code count} strongest cards of the hand, weakest first (all of them when the hand is smaller). */
    public static List<Card> best(List<Card> hand, int count, RankOrder order) {
        List<Card> sorted = HandSort.sorted(hand, order);
        int taken = Math.max(0, Math.min(count, sorted.size()));
        return sorted.subList(sorted.size() - taken, sorted.size());
    }

    /** The {@code count} weakest cards of the hand, weakest first, even out of a pair. */
    public static List<Card> lowest(List<Card> hand, int count, RankOrder order) {
        List<Card> sorted = HandSort.sorted(hand, order);
        return sorted.subList(0, Math.max(0, Math.min(count, sorted.size())));
    }
}
