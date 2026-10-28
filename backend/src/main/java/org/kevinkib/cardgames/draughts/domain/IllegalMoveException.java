package org.kevinkib.cardgames.draughts.domain;

/** No legal move has this start, end and captured set. */
public class IllegalMoveException extends DraughtsException {

    public static final String CODE = "draughts.errors.illegalMove";

    public IllegalMoveException() {
        super(CODE);
    }
}
