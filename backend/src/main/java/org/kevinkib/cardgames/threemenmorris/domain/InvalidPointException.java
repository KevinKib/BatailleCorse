package org.kevinkib.cardgames.threemenmorris.domain;

/** The point is not one of the nine points of the board. */
public class InvalidPointException extends ThreeMenMorrisException {

    public InvalidPointException(int point) {
        super("No such point: " + point);
    }
}
