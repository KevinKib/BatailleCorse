package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * The solver result for Picaria on its thirteen-point board, with the centre locked during placement (the
 * rule of the game) and without the lock, next to Tapatan and Achi. The values are what the retrograde
 * analysis gives and are pinned here as the record behind the comparison table of the spec.
 */
class PicariaSolverResultTest {

    private static final MorrisRules PICARIA_UNLOCKED = new MorrisRules(MorrisRules.PICARIA.geometry(), 3,
            MorrisRules.PlacementRule.FREE);

    private static MorrisPosition opening(MorrisRules rules) {
        return new ThreeMenMorris(GameId.generate(), rules, new PlayerId(0)).position();
    }

    private static MorrisSolver.Solution solve(MorrisRules rules) {
        return MorrisSolver.forRules(rules).solve(opening(rules));
    }

    @Test
    void picariaWithTheCentreLockedIsWonByWhiteInSevenPlies() {
        assertThat(solve(MorrisRules.PICARIA), is(new MorrisSolver.Solution(MorrisSolver.Outcome.WIN, 7)));
    }

    @Test
    void picariaWithoutTheLockIsWonByWhiteInFivePlies() {
        assertThat(solve(PICARIA_UNLOCKED), is(new MorrisSolver.Solution(MorrisSolver.Outcome.WIN, 5)));
    }

    @Test
    void tapatanAndAchiForComparison() {
        assertThat(solve(MorrisRules.TAPATAN), is(new MorrisSolver.Solution(MorrisSolver.Outcome.WIN, 9)));
        assertThat(solve(MorrisRules.ACHI), is(new MorrisSolver.Solution(MorrisSolver.Outcome.WIN, 11)));
    }

    @Test
    void onlyTheSideMiddlesAreWinningFirstPlacements() {
        MorrisSolver solver = MorrisSolver.forRules(MorrisRules.PICARIA);
        MorrisPosition start = opening(MorrisRules.PICARIA);
        List<Integer> winning = new java.util.ArrayList<>();
        for (MorrisDecision decision : MorrisTransitions.legal(start)) {
            MorrisPosition next = ((MorrisTransitions.Outcome.Next) MorrisTransitions.apply(start, decision)).position();
            if (solver.solve(next).outcome() == MorrisSolver.Outcome.LOSS) {
                winning.add(((MorrisDecision.Place) decision).point());
            }
        }
        assertThat(winning, is(List.of(1, 3, 5, 7)));
    }

    @Test
    void theNumberOfReachablePositionsIsPinned() {
        assertThat(MorrisSolver.forRules(MorrisRules.PICARIA).size(), is(41_983));
        assertThat(MorrisSolver.forRules(PICARIA_UNLOCKED).size(), is(48_044));
    }

    /**
     * A move that leaves the opponent without any move wins ("blocked" rule). With six pieces on thirteen
     * points it never happens: no reachable play blocks the opponent without also making a line, so the
     * rule is dead code on this board, locked or not.
     */
    @Test
    void noBlockedPositionIsReachable() {
        assertThat(blockingWins(MorrisRules.PICARIA), is(0));
        assertThat(blockingWins(PICARIA_UNLOCKED), is(0));
    }

    /** Number of reachable (position, decision) pairs where the mover wins by blocking, with no line made. */
    private static int blockingWins(MorrisRules rules) {
        Set<MorrisPosition> seen = new HashSet<>();
        Deque<MorrisPosition> queue = new ArrayDeque<>();
        MorrisPosition start = opening(rules);
        seen.add(start);
        queue.add(start);
        int blocking = 0;
        while (!queue.isEmpty()) {
            MorrisPosition position = queue.poll();
            for (MorrisDecision decision : MorrisTransitions.legal(position)) {
                MorrisTransitions.Outcome outcome = MorrisTransitions.apply(position, decision);
                if (outcome instanceof MorrisTransitions.Outcome.Next next) {
                    if (seen.add(next.position())) {
                        queue.add(next.position());
                    }
                } else if (!makesALine(rules, position, decision)) {
                    blocking++;
                }
            }
        }
        return blocking;
    }

    private static boolean makesALine(MorrisRules rules, MorrisPosition position, MorrisDecision decision) {
        Integer[] board = position.board().toArray(new Integer[0]);
        int mover = position.toMove();
        if (decision instanceof MorrisDecision.Place place) {
            board[place.point()] = mover;
        } else if (decision instanceof MorrisDecision.Step step) {
            board[step.from()] = null;
            board[step.to()] = mover;
        }
        for (List<Integer> line : rules.geometry().lines()) {
            if (line.stream().allMatch(point -> board[point] != null && board[point] == mover)) {
                return true;
            }
        }
        return false;
    }
}
