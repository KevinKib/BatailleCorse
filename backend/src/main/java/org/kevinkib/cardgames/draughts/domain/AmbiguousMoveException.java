package org.kevinkib.cardgames.draughts.domain;

/** Several legal moves share this start and end and the command did not say which pieces it takes. */
public class AmbiguousMoveException extends DraughtsException {

    public static final String CODE = "draughts.errors.ambiguousMove";

    public AmbiguousMoveException() {
        super(CODE);
    }
}
