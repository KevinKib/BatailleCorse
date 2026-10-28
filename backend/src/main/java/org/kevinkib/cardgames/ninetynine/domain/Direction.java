package org.kevinkib.cardgames.ninetynine.domain;

/** The direction of play: seat order or the reverse. Reset to {@link #CLOCKWISE} at each round. */
public enum Direction {
    CLOCKWISE, COUNTER_CLOCKWISE;

    public Direction reversed() {
        return this == CLOCKWISE ? COUNTER_CLOCKWISE : CLOCKWISE;
    }
}
