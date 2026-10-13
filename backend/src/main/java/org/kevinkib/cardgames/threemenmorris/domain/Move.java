package org.kevinkib.cardgames.threemenmorris.domain;

public record Move(int from, int to) {

    public Move {
        requireNotNegative(from);
        requireNotNegative(to);
    }

    /** Only a negative point is refused here: the upper bound belongs to the geometry of the game. */
    static void requireNotNegative(int point) {
        if (point < 0) {
            throw new IllegalArgumentException("No such point: " + point);
        }
    }
}
