package org.kevinkib.cardgames.president.domain.card;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RankOrderTest {

    /** The spec ranking, low to high: 3 4 5 6 7 8 9 10 J Q K A 2. */
    private static final List<FrenchRank> LOW_TO_HIGH = List.of(
            FrenchRank.THREE, FrenchRank.FOUR, FrenchRank.FIVE, FrenchRank.SIX, FrenchRank.SEVEN,
            FrenchRank.EIGHT, FrenchRank.NINE, FrenchRank.TEN, FrenchRank.JACK, FrenchRank.QUEEN,
            FrenchRank.KING, FrenchRank.ACE, FrenchRank.TWO);

    static Stream<Arguments> everyRankPair() {
        List<Arguments> pairs = new ArrayList<>();
        for (int i = 0; i < LOW_TO_HIGH.size(); i++) {
            for (int j = 0; j < LOW_TO_HIGH.size(); j++) {
                pairs.add(Arguments.of(LOW_TO_HIGH.get(i), LOW_TO_HIGH.get(j), Integer.signum(i - j)));
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest(name = "{0} against {1} compares as {2}")
    @MethodSource("everyRankPair")
    void standardOrderRanksThreeLowAndTwoHigh(FrenchRank a, FrenchRank b, int expectedSign) {
        RankOrder order = RankOrder.standard();

        assertThat(Integer.signum(order.compare(a, b)), is(expectedSign));
        if (expectedSign > 0) {
            assertThat(order.strength(a), greaterThan(order.strength(b)));
        } else if (expectedSign < 0) {
            assertThat(order.strength(a), lessThan(order.strength(b)));
        }
    }

    @Test
    void ranksAscendingListsTheSpecOrder() {
        assertThat(RankOrder.standard().ranksAscending(), contains(LOW_TO_HIGH.toArray()));
    }

    @Test
    void aJokerHasNoPlaceInTheStandardOrder() {
        assertThrows(IllegalArgumentException.class, () -> RankOrder.standard().strength(FrenchRank.JOKER));
    }
}
