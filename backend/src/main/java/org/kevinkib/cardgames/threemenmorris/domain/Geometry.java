package org.kevinkib.cardgames.threemenmorris.domain;

import java.util.List;
import java.util.stream.IntStream;

/**
 * The shape of a board as data: the number of points (numbered from 0), for each point the points
 * it is adjacent to, and the winning lines. The engine only reads this; a variant is another
 * instance.
 */
public record Geometry(int points, List<List<Integer>> adjacency, List<List<Integer>> lines) {

    /**
     * Board B of the rules: nine points numbered row by row (0 1 2 / 3 4 5 / 6 7 8, the centre is
     * 4), the lines of the square and the two diagonals through the centre. The centre is adjacent
     * to the eight other points, a corner to its two orthogonal neighbours and to the centre, a
     * side middle to its two corners and to the centre.
     */
    public static final Geometry TAPATAN = new Geometry(9,
            IntStream.range(0, 9).mapToObj(Geometry::tapatanNeighbours).toList(),
            List.of(List.of(0, 1, 2), List.of(3, 4, 5), List.of(6, 7, 8),
                    List.of(0, 3, 6), List.of(1, 4, 7), List.of(2, 5, 8),
                    List.of(0, 4, 8), List.of(2, 4, 6)));

    /**
     * Picaria on its nine-point board: Tapatan's board plus the four short diagonals joining the
     * middles of two neighbouring sides (1-3, 3-7, 7-5, 5-1). They join only two points, so they
     * add moves but no winning line.
     */
    public static final Geometry PICARIA = new Geometry(9,
            IntStream.range(0, 9).mapToObj(Geometry::picariaNeighbours).toList(),
            TAPATAN.lines());

    public Geometry {
        if (points < 1) {
            throw new IllegalArgumentException("A geometry has at least one point");
        }
        if (adjacency.size() != points) {
            throw new IllegalArgumentException("One adjacency list per point is required: "
                    + adjacency.size() + " for " + points + " points");
        }
        adjacency = adjacency.stream().map(List::copyOf).toList();
        lines = lines.stream().map(List::copyOf).toList();
        for (int point = 0; point < points; point++) {
            for (int neighbour : adjacency.get(point)) {
                if (neighbour < 0 || neighbour >= points) {
                    throw new IllegalArgumentException("No such point: " + neighbour);
                }
                if (!adjacency.get(neighbour).contains(point)) {
                    throw new IllegalArgumentException("Adjacency is not symmetric between "
                            + point + " and " + neighbour);
                }
            }
        }
        for (List<Integer> line : lines) {
            for (int point : line) {
                if (point < 0 || point >= points) {
                    throw new IllegalArgumentException("No such point: " + point);
                }
            }
        }
    }

    public boolean isPoint(int point) {
        return point >= 0 && point < points;
    }

    public void requireInRange(int point) {
        if (!isPoint(point)) {
            throw new IllegalArgumentException("No such point: " + point);
        }
    }

    public List<Integer> neighbours(int point) {
        return adjacency.get(point);
    }

    public boolean areAdjacent(int a, int b) {
        return neighbours(a).contains(b);
    }

    private static List<Integer> picariaNeighbours(int point) {
        List<Integer> neighbours = new java.util.ArrayList<>(tapatanNeighbours(point));
        for (int[] diagonal : new int[][]{{1, 3}, {3, 7}, {7, 5}, {5, 1}}) {
            if (point == diagonal[0]) {
                neighbours.add(diagonal[1]);
            } else if (point == diagonal[1]) {
                neighbours.add(diagonal[0]);
            }
        }
        return neighbours;
    }

    /** Orthogonal neighbours, plus the centre, which every other point reaches along a line or a diagonal. */
    private static List<Integer> tapatanNeighbours(int point) {
        return IntStream.range(0, 9)
                .filter(other -> other != point)
                .filter(other -> Math.abs(point / 3 - other / 3) + Math.abs(point % 3 - other % 3) == 1
                        || point == 4 || other == 4)
                .boxed()
                .toList();
    }
}
