package org.kevinkib.cardgames.threemenmorris.domain.offline;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * The compact solver against a naive search on a reduced game (the Six board, fewer pieces each). Run with
 * three pieces each in the default suite; {@link SixMenMorrisSolveRunTest} repeats it with four.
 */
class SixMenSolverSmallTest {

    static SixMenSolver.Pos toPos(BruteForceMorris.Node node) {
        int mover = node.toMove();
        int me = 0;
        int opp = 0;
        for (int point = 0; point < node.board().size(); point++) {
            if (node.board().get(point) == mover) {
                me |= 1 << point;
            } else if (node.board().get(point) == 1 - mover) {
                opp |= 1 << point;
            }
        }
        return new SixMenSolver.Pos(me, opp, node.hand(mover), node.hand(1 - mover));
    }

    /** Solves both ways and compares the verdict of every position the naive search reaches. */
    static void assertAgree(MorrisRules rules) {
        BruteForceMorris brute = new BruteForceMorris(rules);
        brute.solve();
        SixMenSolver compact = new SixMenSolver(rules);
        compact.solve();
        int compared = 0;
        for (BruteForceMorris.Node node : brute.nodes()) {
            if (node.reduced()) {
                continue;
            }
            BruteForceMorris.Verdict expected = brute.verdict(node);
            SixMenSolver.Verdict actual = compact.verdict(toPos(node));
            assertThat("position " + node, actual, is(new SixMenSolver.Verdict(expected.outcome(), expected.depth())));
            compared++;
        }
        assertThat("the naive search reached something", compared > 1000, is(true));
    }

    @Test
    void theCompactSolverAgreesWithTheNaiveSearchOnThreePiecesEach() {
        assertAgree(SixMenSolverIndexTest.reducedRules(3));
    }

    @Test
    void aPositionAMillAwayFromTheEndIsWonInOnePly() {
        SixMenSolver solver = new SixMenSolver(SixMenSolverIndexTest.reducedRules(4));
        solver.solve();
        // 3 -> 2 closes 0-1-2 and leaves the opponent two pieces.
        assertThat(solver.verdict(new SixMenSolver.Pos((1 << 0) | (1 << 1) | (1 << 3), (1 << 5) | (1 << 12) | (1 << 14), 0, 0)),
                is(new SixMenSolver.Verdict(1, 1)));
        // A blocked mover has lost before moving.
        assertThat(solver.verdict(new SixMenSolver.Pos(0b111, (1 << 7) | (1 << 9) | (1 << 3), 0, 0)),
                is(new SixMenSolver.Verdict(-1, 0)));
    }
}
