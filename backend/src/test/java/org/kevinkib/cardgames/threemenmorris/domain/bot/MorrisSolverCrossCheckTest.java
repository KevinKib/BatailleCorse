package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisVariant;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * A second opinion on the solver, by another algorithm: a plain depth-limited minimax over the same
 * transitions. It cannot decide draws (the movement phase cycles) but it can prove a forced win within a
 * number of plies, which pins the solver's depth for the opening of each variant.
 */
class MorrisSolverCrossCheckTest {

    private record Query(MorrisPosition position, int plies) {
    }

    private final Map<Query, Boolean> moverWins = new HashMap<>();
    private final Map<Query, Boolean> moverLoses = new HashMap<>();

    /** The seat to move can force a win within {@code plies} plies, whatever the opponent answers. */
    private boolean moverForcesWin(MorrisPosition position, int plies) {
        if (plies <= 0) {
            return false;
        }
        Query query = new Query(position, plies);
        Boolean known = moverWins.get(query);
        if (known != null) {
            return known;
        }
        boolean result = false;
        for (MorrisDecision decision : MorrisTransitions.legal(position)) {
            MorrisTransitions.Outcome outcome = MorrisTransitions.apply(position, decision);
            if (outcome instanceof MorrisTransitions.Outcome.Win
                    || outcome instanceof MorrisTransitions.Outcome.Next next
                    && moverIsLost(next.position(), plies - 1)) {
                result = true;
                break;
            }
        }
        moverWins.put(query, result);
        return result;
    }

    /** The seat to move is certain to lose within {@code plies} plies, whatever it plays. */
    private boolean moverIsLost(MorrisPosition position, int plies) {
        if (plies < 0) {
            return false;
        }
        Query query = new Query(position, plies);
        Boolean known = moverLoses.get(query);
        if (known != null) {
            return known;
        }
        boolean result = true;
        for (MorrisDecision decision : MorrisTransitions.legal(position)) {
            MorrisTransitions.Outcome outcome = MorrisTransitions.apply(position, decision);
            if (!(outcome instanceof MorrisTransitions.Outcome.Next next)
                    || !moverForcesWin(next.position(), plies - 1)) {
                result = false;
                break;
            }
        }
        moverLoses.put(query, result);
        return result;
    }

    @ParameterizedTest
    @EnumSource(MorrisVariant.class)
    void theOpeningIsAWinForWhiteInExactlyTheDepthTheSolverGives(MorrisVariant variant) {
        MorrisPosition opening = new ThreeMenMorris(GameId.generate(), variant.rules(), new PlayerId(0)).position();
        MorrisSolver.Solution solution = MorrisSolver.forRules(variant.rules()).solve(opening);

        assertThat(solution.outcome(), is(MorrisSolver.Outcome.WIN));
        assertThat("forced win within the solver's depth", moverForcesWin(opening, solution.depth()), is(true));
        assertThat("no faster forced win", moverForcesWin(opening, solution.depth() - 1), is(false));
    }

    @Test
    void picariaWithTheCentreLockedIsWonByWhiteInSevenPlies() {
        // The pinned fact behind the rule change of the centre: the first move into the centre wins.
        MorrisPosition opening = new ThreeMenMorris(GameId.generate(), MorrisVariant.PICARIA.rules(),
                new PlayerId(0)).position();

        assertThat(MorrisSolver.forRules(MorrisVariant.PICARIA.rules()).solve(opening),
                is(new MorrisSolver.Solution(MorrisSolver.Outcome.WIN, 7)));
        assertThat(List.of(moverForcesWin(opening, 7), moverForcesWin(opening, 6)), is(List.of(true, false)));
    }
}
