package org.kevinkib.cardgames.president.domain.scoring;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.rules.Scoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class MatchScoringTest {

    /**
     * One row per (scoring, players) of variant V18: the points of each place from first to last. The
     * finishing order is the seats from the last to the first, so a place is never confused with a seat.
     */
    @ParameterizedTest(name = "{0} with {1} players")
    @CsvSource(delimiter = '|', value = {
            "RANK     | 2 | 1 0",
            "RANK     | 3 | 2 1 0",
            "RANK     | 4 | 3 2 1 0",
            "RANK     | 8 | 7 6 5 4 3 2 1 0",
            "TITLES   | 2 | 2 0",
            "TITLES   | 3 | 2 0 0",
            "TITLES   | 4 | 2 1 0 0",
            "TITLES   | 5 | 2 1 0 0 0",
            "TITLES   | 8 | 2 1 0 0 0 0 0 0",
            "ZERO_SUM | 2 | 1 -1",
            "ZERO_SUM | 3 | 1 0 -1",
            "ZERO_SUM | 4 | 2 1 -1 -2",
            "ZERO_SUM | 5 | 2 1 0 -1 -2",
            "ZERO_SUM | 6 | 3 2 1 -1 -2 -3",
            "ZERO_SUM | 7 | 3 2 1 0 -1 -2 -3",
            "ZERO_SUM | 8 | 4 3 2 1 -1 -2 -3 -4",
    })
    void pointsByPlace(Scoring scoring, int players, String expected) {
        List<PlayerId> order = new ArrayList<>();
        for (int seat = players - 1; seat >= 0; seat--) {
            order.add(new PlayerId(seat));
        }

        Map<PlayerId, Integer> points = MatchScoring.points(scoring, order);

        List<Integer> byPlace = order.stream().map(points::get).toList();
        assertThat(byPlace, is(Arrays.stream(expected.split(" ")).map(Integer::parseInt).toList()));
        assertThat(points.keySet().stream().toList(), is(order));
    }

    @ParameterizedTest
    @CsvSource({"RANK", "TITLES", "ZERO_SUM"})
    void anEmptyFinishingOrderScoresNothing(Scoring scoring) {
        assertThat(MatchScoring.points(scoring, List.of()), is(Map.of()));
    }
}
