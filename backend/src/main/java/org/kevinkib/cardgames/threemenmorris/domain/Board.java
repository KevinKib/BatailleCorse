package org.kevinkib.cardgames.threemenmorris.domain;

import java.util.List;

/**
 * The geometry of Tapatan (board B of the rules), kept as a thin view of {@link Geometry#TAPATAN}
 * for the records that validate a point without knowing the game ({@link Move}, {@link MorrisEvent})
 * and for the v1 tests. The engine itself reads the geometry of its {@link MorrisRules}.
 */
final class Board {

    static final int POINTS = Geometry.TAPATAN.points();

    /** The eight winning lines, each in ascending point order. */
    static final List<List<Integer>> LINES = Geometry.TAPATAN.lines();

    private Board() {
    }

    static boolean isPoint(int point) {
        return Geometry.TAPATAN.isPoint(point);
    }

    static void requireInRange(int point) {
        Geometry.TAPATAN.requireInRange(point);
    }

    static List<Integer> neighbours(int point) {
        return Geometry.TAPATAN.neighbours(point);
    }

    static boolean areAdjacent(int a, int b) {
        return Geometry.TAPATAN.areAdjacent(a, b);
    }
}
