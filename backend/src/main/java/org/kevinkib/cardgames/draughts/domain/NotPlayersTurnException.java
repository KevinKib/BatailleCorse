package org.kevinkib.cardgames.draughts.domain;

/** The sender is not the seat to play. */
public class NotPlayersTurnException extends DraughtsException {

    public static final String CODE = "draughts.errors.notYourTurn";

    public NotPlayersTurnException() {
        super(CODE);
    }
}
