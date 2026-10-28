package org.kevinkib.cardgames.kilodemerde.domain;

/** The command does not belong to the current phase. */
public class WrongPhaseException extends KiloDeMerdeException {

    public WrongPhaseException() {
        super("This action is not available now");
    }
}
