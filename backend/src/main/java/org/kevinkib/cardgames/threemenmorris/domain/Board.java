package org.kevinkib.cardgames.threemenmorris.domain;

import java.util.List;
import java.util.stream.IntStream;

/**
 * The geometry of board B of the rules: nine points numbered row by row (0 1 2 / 3 4 5 / 6 7 8, the
 * centre is 4), the lines of the square and the two diagonals through the centre. The centre is
 * adjacent to the eight other points, a corner to its two orthogonal neighbours and to the centre,
 * a side middle to its two corners and to the centre. Board A (without diagonals) is not played.
 */
final class Board {

    static final int POINTS = 9;
    static final int CENTRE = 4;

    /** The eight winning lines, each in ascending point order. */
    static final List<List<Integer>> LINES = List.of(
            List.of(0, 1, 2), List.of(3, 4, 5), List.of(6, 7, 8),
            List.of(0, 3, 6), List.of(1, 4, 7), List.of(2, 5, 8),
            List.of(0, 4, 8), List.of(2, 4, 6));

    private static final List<List<Integer>> NEIGHBOURS = IntStream.range(0, POINTS)
            .mapToObj(Board::computeNeighbours)
            .toList();

    private Board() {
    }

    static boolean isPoint(int point) {
        return point >= 0 && point < POINTS;
    }

    static List<Integer> neighbours(int point) {
        return NEIGHBOURS.get(point);
    }

    static boolean areAdjacent(int a, int b) {
        return neighbours(a).contains(b);
    }

    /** Orthogonal neighbours, plus the centre, which every other point reaches along a line or a diagonal. */
    private static List<Integer> computeNeighbours(int point) {
        return IntStream.range(0, POINTS)
                .filter(other -> other != point)
                .filter(other -> orthogonal(point, other) || point == CENTRE || other == CENTRE)
                .boxed()
                .toList();
    }

    private static boolean orthogonal(int a, int b) {
        return Math.abs(a / 3 - b / 3) + Math.abs(a % 3 - b % 3) == 1;
    }
}
