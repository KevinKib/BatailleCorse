package org.kevinkib.cardgames.threemenmorris.domain;

/** The two points are not neighbours along a line: pieces step, they never jump. */
public class NotAdjacentException extends ThreeMenMorrisException {

    public NotAdjacentException(int from, int to) {
        super("Point " + to + " is not adjacent to point " + from);
    }
}
