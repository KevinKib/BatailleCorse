package org.kevinkib.cardgames.president.domain.rules;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class MatchLengthTest {

    /** One row per length of variant V18; totals are the players' points after the round just scored. */
    @ParameterizedTest(name = "{0} after {1} rounds, scoring {2}, totals [{3}] -> over {4}")
    @CsvSource(delimiter = '|', value = {
            "ROUNDS_3  | 2  | RANK     | 5 4 0    | false",
            "ROUNDS_3  | 3  | RANK     | 5 4 0    | true",
            "ROUNDS_5  | 4  | RANK     | 5 4 0    | false",
            "ROUNDS_5  | 5  | RANK     | 5 4 0    | true",
            "ROUNDS_10 | 9  | TITLES   | 5 4 0    | false",
            "ROUNDS_10 | 10 | TITLES   | 5 4 0    | true",
            "ROUNDS_5  | 5  | ZERO_SUM | -3 1 2   | true",
            "POINTS_11 | 3  | RANK     | 10 3 0   | false",
            "POINTS_11 | 3  | RANK     | 11 3 0   | true",
            "POINTS_11 | 3  | RANK     | 12 13 1  | true",
            "POINTS_21 | 9  | TITLES   | 20 4 0   | false",
            "POINTS_21 | 9  | TITLES   | 21 4 0   | true",
            "POINTS_21 | 10 | RANK     | 4 4 0    | false",
            "POINTS_11 | 9  | ZERO_SUM | 5 -1 -4  | false",
            "POINTS_11 | 10 | ZERO_SUM | 5 -1 -4  | true",
            "POINTS_21 | 10 | ZERO_SUM | 1 0 -1   | true",
            "POINTS_11 | 4  | ZERO_SUM | 11 0 -11 | true",
            "ENDLESS   | 1  | RANK     | 5 4 0    | false",
            "ENDLESS   | 20 | RANK     | 99 4 0   | false",
            "ENDLESS   | 500| TITLES   | 999 0 0  | false",
            "ENDLESS   | 20 | ZERO_SUM | 5 -1 -4  | false",
    })
    void isOver(MatchLength length, int roundsPlayed, Scoring scoring, String totals, boolean expected) {
        List<Integer> points = Arrays.stream(totals.split(" ")).map(Integer::parseInt).toList();

        assertThat(length.isOver(roundsPlayed, scoring, points), is(expected));
    }
}
