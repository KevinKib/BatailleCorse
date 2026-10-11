package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

/**
 * The geometry of board B of the rules: the lines of the square plus the two diagonals through the
 * centre. Points are numbered row by row: 0 1 2 / 3 4 5 / 6 7 8 (4 is the centre).
 */
class BoardTest {

    @Test
    void thereAreExactlyEightWinningLines_threeRowsThreeColumnsAndBothDiagonals() {
        Set<Set<Integer>> lines = new HashSet<>();
        for (List<Integer> line : Board.LINES) {
            lines.add(new HashSet<>(line));
        }

        assertThat(Board.LINES.size(), is(8));
        assertThat(lines, containsInAnyOrder(
                Set.of(0, 1, 2), Set.of(3, 4, 5), Set.of(6, 7, 8),
                Set.of(0, 3, 6), Set.of(1, 4, 7), Set.of(2, 5, 8),
                Set.of(0, 4, 8), Set.of(2, 4, 6)));
    }

    @Test
    void theCentreIsAdjacentToTheEightOtherPoints() {
        assertThat(Board.neighbours(4), containsInAnyOrder(0, 1, 2, 3, 5, 6, 7, 8));
    }

    @Test
    void everyCornerIsAdjacentToItsTwoOrthogonalNeighboursAndToTheCentre() {
        assertThat(Board.neighbours(0), containsInAnyOrder(1, 3, 4));
        assertThat(Board.neighbours(2), containsInAnyOrder(1, 5, 4));
        assertThat(Board.neighbours(6), containsInAnyOrder(3, 7, 4));
        assertThat(Board.neighbours(8), containsInAnyOrder(5, 7, 4));
    }

    @Test
    void everySideMiddleIsAdjacentToItsTwoCornersAndToTheCentre() {
        assertThat(Board.neighbours(1), containsInAnyOrder(0, 2, 4));
        assertThat(Board.neighbours(3), containsInAnyOrder(0, 6, 4));
        assertThat(Board.neighbours(5), containsInAnyOrder(2, 8, 4));
        assertThat(Board.neighbours(7), containsInAnyOrder(6, 8, 4));
    }

    @Test
    void adjacencyIsSymmetricAndThereIsNoJumpAcrossTheBoard() {
        for (int a = 0; a < 9; a++) {
            for (int b : Board.neighbours(a)) {
                assertThat(Board.neighbours(b).contains(a), is(true));
            }
        }
        assertThat(Board.areAdjacent(0, 2), is(false));
        assertThat(Board.areAdjacent(1, 7), is(false));
        assertThat(Board.areAdjacent(0, 8), is(false));
    }
}
