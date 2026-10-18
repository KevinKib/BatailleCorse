package org.kevinkib.cardgames.threemenmorris.domain.offline;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The successor generator of the offline solver on hand-written positions of the spec. Positions are seen
 * from the seat to move: {@code Pos(me, opp, handMe, handOpp)} with bit masks over the 16 points (outer
 * square 0..7 clockwise, inner square 8..15; lines 0-1-2, 2-3-4, 4-5-6, 6-7-0 and 8-9-10, 10-11-12,
 * 12-13-14, 14-15-8). A successor is seen from the opponent, who moves next.
 */
class SixMenSolverRulesTest {

    private final SixMenSolver solver = new SixMenSolver(MorrisRules.SIX_MEN);

    private static int mask(int... points) {
        int mask = 0;
        for (int point : points) {
            mask |= 1 << point;
        }
        return mask;
    }

    private List<SixMenSolver.Succ> successors(int me, int opp, int handMe, int handOpp) {
        return solver.successors(new SixMenSolver.Pos(me, opp, handMe, handOpp));
    }

    @Test
    void placementWithoutMillPassesTheTurnAndEmptiesOneHand() {
        List<SixMenSolver.Succ> successors = successors(mask(0), mask(5), 5, 5);
        assertThat(successors.size(), is(14));
        assertThat(successors.stream().map(SixMenSolver.Succ::next).toList(),
                hasItem(new SixMenSolver.Pos(mask(5), mask(0, 9), 5, 4)));
    }

    @Test
    void aMillDuringPlacementRemovesAnOpponentPieceOutsideEveryMill() {
        // Mover holds 0 and 1 and places on 2; the opponent has 5 (the only piece) and 4 in hand.
        List<SixMenSolver.Succ> successors = successors(mask(0, 1), mask(5), 4, 4);
        assertThat(successors.size(), is(13));
        assertThat(successors.stream().map(SixMenSolver.Succ::next).toList(),
                hasItem(new SixMenSolver.Pos(0, mask(0, 1, 2), 4, 3)));
    }

    @Test
    void aProtectedPieceCannotBeTakenWhileAnotherPieceIsOutsideEveryMill() {
        // Moving 3 -> 2 closes 0-1-2. The opponent has the mill 8-9-10 and the lone piece 13.
        List<SixMenSolver.Succ> successors = successors(mask(0, 1, 3, 6), mask(8, 9, 10, 13), 0, 0);
        List<SixMenSolver.Pos> afterMill = successors.stream().map(SixMenSolver.Succ::next)
                .filter(next -> next != null && next.opp() == mask(0, 1, 2, 6)).toList();
        assertThat(afterMill, is(List.of(new SixMenSolver.Pos(mask(8, 9, 10), mask(0, 1, 2, 6), 0, 0))));
    }

    @Test
    void whenEveryOpponentPieceIsInAMillAnyOfThemCanBeTaken() {
        List<SixMenSolver.Succ> successors = successors(mask(0, 1, 3), mask(8, 9, 10, 12, 13, 14), 0, 0);
        long millMoves = successors.stream().filter(s -> s.next() != null && s.next().opp() == mask(0, 1, 2)).count();
        assertThat(millMoves, is(6L));
    }

    @Test
    void aDoubleMillRemovesOnlyOnePiece() {
        // Placing on corner 2 closes 0-1-2 and 2-3-4 at once; two loose opponent pieces give two successors.
        List<SixMenSolver.Succ> successors = successors(mask(0, 1, 3, 4), mask(10, 12), 2, 2);
        List<SixMenSolver.Pos> doubles = successors.stream().map(SixMenSolver.Succ::next)
                .filter(next -> next.opp() == mask(0, 1, 2, 3, 4)).toList();
        assertThat(doubles.size(), is(2));
        assertThat(doubles, hasItem(new SixMenSolver.Pos(mask(12), mask(0, 1, 2, 3, 4), 2, 1)));
        assertThat(doubles, hasItem(new SixMenSolver.Pos(mask(10), mask(0, 1, 2, 3, 4), 2, 1)));
    }

    @Test
    void movingOutOfAMillAndBackFormsItAgain() {
        // 0-1-2 is a mill; the piece on 1 steps to 9 and the next turn steps back: closing it again is a mill.
        List<SixMenSolver.Succ> successors = successors(mask(0, 2, 9), mask(5, 12, 14, 15), 0, 0);
        List<SixMenSolver.Pos> back = successors.stream().map(SixMenSolver.Succ::next)
                .filter(next -> (next.opp() & mask(0, 1, 2)) == mask(0, 1, 2)).toList();
        assertThat(back.size(), is(4));
    }

    @Test
    void reducingTheOpponentToTwoPiecesIsAnImmediateWin() {
        List<SixMenSolver.Succ> successors = successors(mask(0, 1, 3), mask(5, 12, 14), 0, 0);
        assertThat(successors.stream().filter(SixMenSolver.Succ::immediateWin).count(), is(3L));
        assertThat(successors.stream().filter(s -> s.next() == null).count(), is(3L));
    }

    @Test
    void aPlayerWithAPieceInHandCountsItAgainstTheTwoPieceLoss() {
        // The opponent has three pieces on the board and one in hand: taking one leaves three, no win.
        List<SixMenSolver.Succ> successors = successors(mask(0, 1, 3), mask(5, 12, 14), 1, 1);
        assertThat(successors.stream().anyMatch(SixMenSolver.Succ::immediateWin), is(false));
    }

    @Test
    void aBlockedPlayerHasNoSuccessor() {
        // 0 can reach 1 and 7, 1 reaches 0, 2 and 9, 2 reaches 1 and 3: all taken by the opponent or by itself.
        assertThat(successors(mask(0, 1, 2), mask(7, 9, 3), 0, 0).isEmpty(), is(true));
    }

    @Test
    void thereIsNoFlyingWithThreePieces() {
        List<SixMenSolver.Succ> successors = successors(mask(0, 4, 12), mask(2, 6, 10), 0, 0);
        // 0 -> 1, 0 -> 7, 4 -> 3, 4 -> 5, 12 -> 11, 12 -> 13: six moves, not 3 * 10.
        assertThat(successors.size(), is(6));
        assertThat(successors.stream().map(SixMenSolver.Succ::next).toList(),
                not(hasItem(new SixMenSolver.Pos(mask(2, 6, 10), mask(4, 12, 15), 0, 0))));
    }

    @Test
    void aMillWithNoOpponentPieceRemovesNothingAndPassesTheTurn() {
        List<SixMenSolver.Succ> successors = successors(mask(0, 1), 0, 4, 3);
        assertThat(successors.stream().map(SixMenSolver.Succ::next).toList(),
                hasItem(new SixMenSolver.Pos(0, mask(0, 1, 2), 3, 3)));
    }

    @Test
    void rulesThatAreNotMillRulesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new SixMenSolver(MorrisRules.TAPATAN));
    }
}
