package org.kevinkib.cardgames.draughts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.draughts.domain.BoardGeometry.Direction;

class BoardGeometryTest {

    @Test
    void cornersOfTheLongDiagonalAreFiveAndFortySix() {
        assertThat(BoardGeometry.row(5)).isEqualTo(0);
        assertThat(BoardGeometry.col(5)).isEqualTo(9);
        assertThat(BoardGeometry.row(46)).isEqualTo(9);
        assertThat(BoardGeometry.col(46)).isEqualTo(0);
        assertThat(BoardGeometry.row(1)).isEqualTo(0);
        assertThat(BoardGeometry.col(1)).isEqualTo(1);
        assertThat(BoardGeometry.col(50)).isEqualTo(8);
    }

    @Test
    void bandsAndBaseRows() {
        for (int n : new int[] {6, 16, 26, 36, 46}) {
            assertThat(BoardGeometry.col(n)).isZero();
        }
        for (int n : new int[] {5, 15, 25, 35, 45}) {
            assertThat(BoardGeometry.col(n)).isEqualTo(9);
        }
        for (int n = 1; n <= 5; n++) {
            assertThat(BoardGeometry.row(n)).isZero();
        }
        for (int n = 46; n <= 50; n++) {
            assertThat(BoardGeometry.row(n)).isEqualTo(9);
        }
    }

    @Test
    void squareAtInvertsRowAndCol() {
        for (int n = 1; n <= 50; n++) {
            assertThat(BoardGeometry.squareAt(BoardGeometry.row(n), BoardGeometry.col(n))).isEqualTo(n);
        }
        assertThat(BoardGeometry.squareAt(0, 0)).isZero();
        assertThat(BoardGeometry.squareAt(-1, 3)).isZero();
        assertThat(BoardGeometry.squareAt(3, 10)).isZero();
    }

    @Test
    void promotionRows() {
        assertThat(BoardGeometry.promotionRow(Colour.WHITE)).isZero();
        assertThat(BoardGeometry.promotionRow(Colour.BLACK)).isEqualTo(9);
        assertThat(BoardGeometry.isPromotionSquare(Colour.WHITE, 3)).isTrue();
        assertThat(BoardGeometry.isPromotionSquare(Colour.WHITE, 46)).isFalse();
        assertThat(BoardGeometry.isPromotionSquare(Colour.BLACK, 50)).isTrue();
    }

    @Test
    void raysAndNeighbours() {
        assertThat(BoardGeometry.ray(46, Direction.NORTH_EAST))
                .containsExactly(41, 37, 32, 28, 23, 19, 14, 10, 5);
        assertThat(BoardGeometry.ray(46, Direction.NORTH_WEST)).isEmpty();
        assertThat(BoardGeometry.ray(5, Direction.SOUTH_WEST))
                .containsExactly(10, 14, 19, 23, 28, 32, 37, 41, 46);
        assertThat(BoardGeometry.neighbour(28, Direction.NORTH_WEST)).isEqualTo(22);
        assertThat(BoardGeometry.neighbour(28, Direction.SOUTH_EAST)).isEqualTo(33);
        assertThat(BoardGeometry.neighbour(6, Direction.NORTH_WEST)).isZero();
    }

    @Test
    void raysFormExactlySeventeenMaximalDiagonals() {
        Set<List<Integer>> diagonals = new HashSet<>();
        for (int n = 1; n <= 50; n++) {
            addMaximalLine(diagonals, n, Direction.NORTH_EAST, Direction.SOUTH_WEST);
            addMaximalLine(diagonals, n, Direction.SOUTH_EAST, Direction.NORTH_WEST);
        }
        assertThat(diagonals).hasSize(17);
        assertThat(diagonals).contains(List.of(46, 41, 37, 32, 28, 23, 19, 14, 10, 5));
    }

    private static void addMaximalLine(Set<List<Integer>> into, int square, Direction forward, Direction back) {
        int start = square;
        while (BoardGeometry.neighbour(start, back) != 0) {
            start = BoardGeometry.neighbour(start, back);
        }
        List<Integer> line = new ArrayList<>();
        line.add(start);
        for (int s : BoardGeometry.ray(start, forward)) {
            line.add(s);
        }
        if (line.size() >= 2) {
            into.add(line);
        }
    }

    @Test
    void rejectsSquaresOutOfRange() {
        assertThatThrownBy(() -> BoardGeometry.row(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoardGeometry.col(51)).isInstanceOf(IllegalArgumentException.class);
    }
}
