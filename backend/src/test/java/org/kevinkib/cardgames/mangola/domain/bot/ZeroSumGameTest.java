package org.kevinkib.cardgames.mangola.domain.bot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

class ZeroSumGameTest {

    /** Rock, paper, scissors with a win worth 1, a draw 0.5 and a loss 0 for the row player. */
    private static final double[][] RPS = {
            {0.5, 0, 1},
            {1, 0.5, 0},
            {0, 1, 0.5}
    };

    @Test
    void fictitiousPlayFindsTheUniformMixOfRockPaperScissors() {
        ZeroSumGame.Solution solution = ZeroSumGame.solve(RPS, 200_000);
        for (double p : solution.rowMix()) {
            assertThat(p, closeTo(1.0 / 3, 0.01));
        }
        for (double p : solution.columnMix()) {
            assertThat(p, closeTo(1.0 / 3, 0.01));
        }
        assertThat(solution.lowerValue(), closeTo(0.5, 0.01));
        assertThat(solution.upperValue(), closeTo(0.5, 0.01));
        assertThat(solution.gap(), lessThan(0.01));
    }

    @Test
    void anUnbalancedGameIsSolvedWithItsValue() {
        // The row player wins 0.8 with row 0 against column 0, 0.3 against column 1; 0.4 and 0.6 with row 1.
        // The value is (0.8 * 0.6 - 0.3 * 0.4) / (0.8 + 0.6 - 0.3 - 0.4) = 0.36 / 0.7, the row mix (2/7, 5/7).
        double[][] game = {{0.8, 0.3}, {0.4, 0.6}};
        ZeroSumGame.Solution solution = ZeroSumGame.solve(game, 400_000);
        assertThat(solution.rowMix()[0], closeTo(2.0 / 7, 0.01));
        assertThat(solution.columnMix()[0], closeTo(3.0 / 7, 0.01));
        assertThat((solution.lowerValue() + solution.upperValue()) / 2, closeTo(0.36 / 0.7, 0.005));
    }

    @Test
    void aRowNoBetterThanAnotherAgainstEveryColumnIsDominated() {
        double[][] game = {
                {0.6, 0.7, 0.5},
                {0.6, 0.4, 0.5},
                {0.9, 0.1, 0.5}
        };
        assertThat(ZeroSumGame.dominatedRows(game, 0.0), contains(1));
        assertThat(ZeroSumGame.dominatedRows(RPS, 0.0), is(empty()));
    }

    @Test
    void aColumnNoBetterForTheColumnPlayerIsDominated() {
        // The column player wants low values: column 2 is never lower than column 0.
        double[][] game = {
                {0.2, 0.9, 0.3},
                {0.5, 0.1, 0.6}
        };
        assertThat(ZeroSumGame.dominatedColumns(game, 0.0), contains(2));
    }

    @Test
    void theBestPureRowsAreTheMaximinAndTheBestAgainstAUniformField() {
        double[][] game = {
                {0.9, 0.0, 0.9},
                {0.5, 0.5, 0.5},
                {0.2, 0.3, 0.4}
        };
        assertThat(ZeroSumGame.maximinRow(game), is(1));
        assertThat(ZeroSumGame.bestAgainstUniformRow(game), is(0));
        assertThat(ZeroSumGame.rowValuesAgainst(game, new double[]{0, 1, 0}), is(new double[]{0.0, 0.5, 0.3}));
        assertThat(List.of(ZeroSumGame.maximinRow(game)), contains(1));
    }
}
