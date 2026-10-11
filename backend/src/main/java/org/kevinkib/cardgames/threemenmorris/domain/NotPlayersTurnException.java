package org.kevinkib.cardgames.threemenmorris.domain;

/** The sender is not the seat to play. */
public class NotPlayersTurnException extends ThreeMenMorrisException {

    public NotPlayersTurnException() {
        super("It is not your turn");
    }
}
