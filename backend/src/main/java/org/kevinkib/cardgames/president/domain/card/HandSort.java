package org.kevinkib.cardgames.president.domain.card;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.Comparator;
import java.util.List;

/** Orders a hand from the weakest card to the strongest, suits grouped inside a rank (clubs first). */
public final class HandSort {

    private static final List<FrenchSuit> SUIT_ORDER = List.of(
            FrenchSuit.CLUB, FrenchSuit.DIAMOND, FrenchSuit.HEART, FrenchSuit.SPADE,
            FrenchSuit.BLACK_JOKER, FrenchSuit.RED_JOKER);

    private HandSort() {
    }

    public static List<Card> sorted(List<Card> hand, RankOrder order) {
        return hand.stream()
                .sorted(Comparator.<Card>comparingInt(c -> order.strength(c.getRank()))
                        .thenComparingInt(c -> SUIT_ORDER.indexOf(c.getSuit())))
                .toList();
    }
}
