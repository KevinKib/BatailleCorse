package org.kevinkib.cardgames.ninetynine.domain;

/** The command comes from a seat that is not the one to act. */
public class NotPlayersTurnException extends NinetyNineException {

    public NotPlayersTurnException() {
        super("It is not your turn");
    }
}
