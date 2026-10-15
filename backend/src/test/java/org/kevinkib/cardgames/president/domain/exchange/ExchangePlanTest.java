package org.kevinkib.cardgames.president.domain.exchange;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.president.domain.rules.ExchangeAmount;

import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ExchangePlanTest {

    /**
     * One row per (exchange option, active players) of the spec (3.2 and variant V14): each pair is
     * {@code highPlace-lowPlace:cards}, places counted from 0 at the top of the finishing order. Two players
     * only happen after a forfeit.
     */
    @ParameterizedTest(name = "{0} with {1} players")
    @CsvSource(delimiter = '|', value = {
            "TWO    | 2 | 0-1:2",
            "TWO    | 3 | 0-2:2",
            "TWO    | 4 | 0-3:2 1-2:1",
            "TWO    | 5 | 0-4:2 1-3:1",
            "TWO    | 8 | 0-7:2 1-6:1",
            "ONE    | 2 | 0-1:1",
            "ONE    | 3 | 0-2:1",
            "ONE    | 6 | 0-5:1",
            "ONE    | 8 | 0-7:1",
            "SCALED | 2 | 0-1:1",
            "SCALED | 3 | 0-2:1",
            "SCALED | 4 | 0-3:2 1-2:1",
            "SCALED | 5 | 0-4:2 1-3:1",
            "SCALED | 6 | 0-5:3 1-4:2 2-3:1",
            "SCALED | 7 | 0-6:3 1-5:2 2-4:1",
            "SCALED | 8 | 0-7:4 1-6:3 2-5:2 3-4:1",
            "NONE   | 3 |",
            "NONE   | 8 |",
    })
    void pairsAndAmounts(ExchangeAmount amount, int players, String expected) {
        String actual = ExchangePlan.of(amount, players).pairs().stream()
                .map(p -> p.highPlace() + "-" + p.lowPlace() + ":" + p.count())
                .collect(Collectors.joining(" "));

        assertThat(actual, is(expected == null ? "" : expected));
    }
}
