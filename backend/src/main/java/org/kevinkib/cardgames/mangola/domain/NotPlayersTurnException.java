package org.kevinkib.cardgames.mangola.domain;

/** The sender is not the seat to play. */
public class NotPlayersTurnException extends MangolaException {

    public NotPlayersTurnException() {
        super("It is not your turn");
    }
}
