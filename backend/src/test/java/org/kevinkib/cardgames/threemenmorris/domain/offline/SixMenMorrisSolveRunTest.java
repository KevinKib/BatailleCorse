package org.kevinkib.cardgames.threemenmorris.domain.offline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;

/**
 * Not part of the default suite: solves Six Men's Morris under our exact rules and prints the result. Run with
 * {@code mvn -s ../deploy/docker/settings.xml test -Dtest=SixMenMorrisSolveRunTest -Dmorris.solveSix=true
 * -DargLine=-Xmx2g} (about 50 million states, a few minutes). It first repeats the cross-check against the
 * naive search with four pieces each, so a broken solver stops before the long run. It asserts nothing about
 * the value of the game: the result is pinned in the spec, section "Solver result".
 */
@EnabledIfSystemProperty(named = "morris.solveSix", matches = "true")
class SixMenMorrisSolveRunTest {

    @Test
    void solveSixMenMorris() {
        System.out.println("[solve] cross-check against the naive search, 4 pieces each");
        SixMenSolverSmallTest.assertAgree(SixMenSolverIndexTest.reducedRules(4));
        System.out.println("[solve] cross-check passed");

        SixMenSolver solver = new SixMenSolver(MorrisRules.SIX_MEN);
        long begin = System.nanoTime();
        solver.solve();
        System.out.printf("[solve] solved %d states in %.1f s%n", (long) solver.stateCount(),
                (System.nanoTime() - begin) / 1e9);
        System.out.println(solver.report());
    }
}
