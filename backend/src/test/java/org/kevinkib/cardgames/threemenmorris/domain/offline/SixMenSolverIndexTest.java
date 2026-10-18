package org.kevinkib.cardgames.threemenmorris.domain.offline;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;

import java.math.BigInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** The dense index of the offline solver: counts per layer and rank/unrank round trips. */
class SixMenSolverIndexTest {

    private static long choose(int n, int k) {
        if (k < 0 || k > n) {
            return 0;
        }
        BigInteger result = BigInteger.ONE;
        for (int i = 1; i <= k; i++) {
            result = result.multiply(BigInteger.valueOf(n - k + i)).divide(BigInteger.valueOf(i));
        }
        return result.longValueExact();
    }

    @Test
    void everyLayerHoldsAsManyStatesAsTheWaysToChooseTheTwoPieceSets() {
        SixMenSolver solver = new SixMenSolver(MorrisRules.SIX_MEN);
        long total = 0;
        for (SixMenSolver.Layer layer : solver.layers()) {
            assertThat(layer.size(), is(choose(16, layer.onBoardMe()) * choose(16 - layer.onBoardMe(), layer.onBoardOpp())));
            assertThat(layer.onBoardMe(), is(layer.totalMe() - layer.handMe()));
            assertThat(layer.onBoardOpp(), is(layer.totalOpp() - layer.handOpp()));
            total += layer.size();
        }
        assertThat((long) solver.stateCount(), is(total));
    }

    @Test
    void movementLayersHoldTheNineteenMillionBoardsOfTheSpecEstimate() {
        SixMenSolver solver = new SixMenSolver(MorrisRules.SIX_MEN);
        long movement = solver.layers().stream().filter(l -> l.handsLeft() == 0)
                .mapToLong(SixMenSolver.Layer::size).sum();
        assertThat(movement, is(19_207_188L));
    }

    @Test
    void layersAreListedInSolvingOrder() {
        SixMenSolver solver = new SixMenSolver(MorrisRules.SIX_MEN);
        int previousTotal = 0;
        int previousHands = 0;
        for (SixMenSolver.Layer layer : solver.layers()) {
            int total = layer.totalMe() + layer.totalOpp();
            boolean ordered = total > previousTotal || (total == previousTotal && layer.handsLeft() >= previousHands);
            assertThat("layer " + layer, ordered, is(true));
            previousTotal = total;
            previousHands = layer.handsLeft();
        }
    }

    @Test
    void decodeAndIndexOfAreInverseOnEveryStateOfTheReducedGameAndOnASampleOfTheFullGame() {
        SixMenSolver reduced = new SixMenSolver(reducedRules(3));
        for (int index = 0; index < reduced.stateCount(); index++) {
            assertThat(reduced.indexOf(reduced.decode(index)), is(index));
        }
        SixMenSolver full = new SixMenSolver(MorrisRules.SIX_MEN);
        for (int index = 0; index < full.stateCount(); index += 997) {
            assertThat(full.indexOf(full.decode(index)), is(index));
        }
        assertThat(full.indexOf(full.decode(full.stateCount() - 1)), is(full.stateCount() - 1));
    }

    @Test
    void theOpeningPositionIsTheEmptyBoardWithSixInEachHand() {
        SixMenSolver solver = new SixMenSolver(MorrisRules.SIX_MEN);
        SixMenSolver.Pos start = solver.start();
        assertThat(start, is(new SixMenSolver.Pos(0, 0, 6, 6)));
        assertThat(solver.decode(solver.indexOf(start)), is(start));
    }

    static MorrisRules reducedRules(int pieces) {
        return new MorrisRules(MorrisRules.SIX_MEN.geometry(), pieces, MorrisRules.LineRule.MILL,
                MorrisRules.RemovalRule.PROTECT_MILLS, MorrisRules.FlyingRule.NEVER, 50);
    }
}
