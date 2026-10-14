package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisVariant;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Retrograde analysis of the three variants. The values asserted for the opening are what the real rules
 * give (they were computed with this solver, then cross-checked by the bot tests that replay them).
 */
class MorrisSolverTest {

    private static MorrisPosition opening(MorrisRules rules) {
        return new ThreeMenMorris(GameId.generate(), rules, new PlayerId(0)).position();
    }

    @Test
    void tapatanIsAWhiteWin() {
        assertThat(MorrisSolver.forRules(MorrisRules.TAPATAN).solve(opening(MorrisRules.TAPATAN)).outcome(),
                is(MorrisSolver.Outcome.WIN));
    }

    @Test
    void achiIsAWhiteWin() {
        assertThat(MorrisSolver.forRules(MorrisRules.ACHI).solve(opening(MorrisRules.ACHI)).outcome(),
                is(MorrisSolver.Outcome.WIN));
    }

    /** With the centre closed for the whole placement phase, White moves into it first and wins. */
    @Test
    void picariaWithTheCentreLockedIsAWhiteWin() {
        assertThat(MorrisSolver.forRules(MorrisRules.PICARIA).solve(opening(MorrisRules.PICARIA)).outcome(),
                is(MorrisSolver.Outcome.WIN));
    }

    /** The same board with the centre open from the first placement is the draw the first draft of the spec found. */
    @Test
    void picariaWithTheCentreOpenIsADraw() {
        MorrisRules open = new MorrisRules(MorrisRules.PICARIA.geometry(), 3, MorrisRules.PlacementRule.FREE);

        assertThat(MorrisSolver.forRules(open).solve(opening(open)).outcome(), is(MorrisSolver.Outcome.DRAW));
    }

    @Test
    void theNumberOfReachablePositionsIsPinned() {
        assertThat(MorrisSolver.forRules(MorrisRules.TAPATAN).size(), is(3602));
        assertThat(MorrisSolver.forRules(MorrisRules.ACHI).size(), is(4512));
        assertThat(MorrisSolver.forRules(MorrisRules.PICARIA).size(), is(2545));
    }

    @Test
    void eachVariantIsSolvedFromScratchUnderASecond() {
        for (MorrisVariant variant : MorrisVariant.values()) {
            // A fresh solver: the cache would hide the cost.
            MorrisSolver solver = assertTimeoutPreemptively(Duration.ofSeconds(1),
                    () -> new MorrisSolver(variant.rules()));
            assertThat(variant + " positions", solver.size(), greaterThan(1_000));
        }
    }

    @Test
    void theSolverIsCachedPerRules() {
        assertThat(MorrisSolver.forRules(MorrisRules.ACHI) == MorrisSolver.forRules(MorrisRules.ACHI), is(true));
        assertThat(MorrisSolver.forRules(MorrisRules.ACHI) == MorrisSolver.forRules(MorrisRules.TAPATAN),
                is(false));
    }

    @Test
    void aPositionWhereTheMoverCompletesALineIsAWinInOne() {
        MorrisPosition position = new MorrisPosition(MorrisRules.TAPATAN,
                Arrays.asList(0, 0, null, 1, 1, null, null, null, null), List.of(1, 1), 0);

        MorrisSolver.Solution solution = MorrisSolver.forRules(MorrisRules.TAPATAN).solve(position);

        assertThat(solution.outcome(), is(MorrisSolver.Outcome.WIN));
        assertThat(solution.depth(), is(1));
    }

    @Test
    void theSolutionIsSeenFromTheSeatToMoveWhoeverItIs() {
        MorrisPosition whiteToMove = new MorrisPosition(MorrisRules.TAPATAN,
                Arrays.asList(0, 0, null, 1, 1, null, null, null, null), List.of(1, 1), 0);
        MorrisPosition blackToMove = new MorrisPosition(MorrisRules.TAPATAN,
                Arrays.asList(1, 1, null, 0, 0, null, null, null, null), List.of(1, 1), 1);

        MorrisSolver solver = MorrisSolver.forRules(MorrisRules.TAPATAN);

        assertThat(solver.solve(blackToMove), is(solver.solve(whiteToMove)));
    }

    @Test
    void aPositionNobodyCanReachIsNotSolved() {
        MorrisPosition unreachable = new MorrisPosition(MorrisRules.TAPATAN,
                Arrays.asList(0, 0, 0, 1, 1, 1, null, null, null), List.of(0, 0), 0);

        assertThat(MorrisSolver.forRules(MorrisRules.TAPATAN).contains(unreachable), is(false));
    }
}
