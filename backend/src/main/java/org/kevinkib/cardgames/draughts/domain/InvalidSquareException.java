package org.kevinkib.cardgames.draughts.domain;

/** A square outside 1..50. */
public class InvalidSquareException extends DraughtsException {

    public static final String CODE = "draughts.errors.invalidSquare";

    public InvalidSquareException() {
        super(CODE);
    }
}
