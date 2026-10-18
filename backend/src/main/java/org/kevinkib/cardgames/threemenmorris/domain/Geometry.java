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
     * Picaria on its thirteen-point board (a Zuni board): the nine points of the grid (0..8, numbered
     * as in Tapatan) plus four crossings where the diamond joining the middles of the sides cuts the
     * two corner diagonals. Crossing 9 lies between corner 0 and the centre, 10 between 2 and the centre,
     * 11 between 8 and the centre, 12 between 6 and the centre.
     *
     * <p>Two points are adjacent when they are consecutive along a drawn line: the three rows and three
     * columns of the grid, the corner diagonals (0-9-4-11-8 and 2-10-4-12-6, so a corner is not next to
     * the centre) and the four edges of the diamond (1-9-3, 1-10-5, 5-11-7, 7-12-3). A winning line is
     * the grid's rows and columns plus every three consecutive points along a drawn diagonal: 16 lines.
     */
    public static final Geometry PICARIA = picaria();

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

    private static Geometry picaria() {
        List<List<Integer>> chains = List.of(
                List.of(0, 1, 2), List.of(3, 4, 5), List.of(6, 7, 8),
                List.of(0, 3, 6), List.of(1, 4, 7), List.of(2, 5, 8),
                List.of(0, 9, 4, 11, 8), List.of(2, 10, 4, 12, 6),
                List.of(1, 9, 3), List.of(1, 10, 5), List.of(5, 11, 7), List.of(7, 12, 3));
        List<java.util.Set<Integer>> neighbours = new java.util.ArrayList<>();
        for (int point = 0; point < 13; point++) {
            neighbours.add(new java.util.TreeSet<>());
        }
        List<List<Integer>> lines = new java.util.ArrayList<>();
        for (List<Integer> chain : chains) {
            for (int i = 0; i + 1 < chain.size(); i++) {
                neighbours.get(chain.get(i)).add(chain.get(i + 1));
                neighbours.get(chain.get(i + 1)).add(chain.get(i));
            }
            for (int i = 0; i + 2 < chain.size(); i++) {
                lines.add(chain.subList(i, i + 3));
            }
        }
        return new Geometry(13, neighbours.stream().<List<Integer>>map(List::copyOf).toList(), lines);
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
