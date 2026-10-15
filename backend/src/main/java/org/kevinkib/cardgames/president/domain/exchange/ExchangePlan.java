package org.kevinkib.cardgames.president.domain.exchange;

import org.kevinkib.cardgames.president.domain.rules.ExchangeAmount;

import java.util.ArrayList;
import java.util.List;

/**
 * Who exchanges with whom and how many cards, by place in the previous finishing order (0 is the best
 * place). The low rank gives its best cards, the high rank gives back as many.
 */
public record ExchangePlan(List<Pair> pairs) {

    public ExchangePlan {
        pairs = List.copyOf(pairs);
    }

    /** A pair of places: {@code highPlace} receives {@code count} cards from {@code lowPlace} and returns as many. */
    public record Pair(int highPlace, int lowPlace, int count) {
    }

    /**
     * The pairs for {@code players} active players. {@code two}: President and Scum two cards, then Vice
     * pair one card (from 4 players). {@code one}: the top pair one card. {@code scaled}: with p = players / 2
     * pairs, the i-th from the top (from 1) exchanges p - i + 1 cards. {@code none}: nobody.
     */
    public static ExchangePlan of(ExchangeAmount amount, int players) {
        List<Pair> pairs = new ArrayList<>();
        if (players >= 2) {
            switch (amount) {
                case NONE -> { }
                case ONE -> pairs.add(new Pair(0, players - 1, 1));
                case TWO -> {
                    pairs.add(new Pair(0, players - 1, 2));
                    if (players >= 4) {
                        pairs.add(new Pair(1, players - 2, 1));
                    }
                }
                case SCALED -> {
                    int titlePairs = players / 2;
                    for (int i = 0; i < titlePairs; i++) {
                        pairs.add(new Pair(i, players - 1 - i, titlePairs - i));
                    }
                }
            }
        }
        return new ExchangePlan(pairs);
    }
}
