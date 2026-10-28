package org.kevinkib.cardgames.ninetynine.domain;

/** The command does not belong to the current phase. */
public class WrongPhaseException extends NinetyNineException {

    public WrongPhaseException() {
        super("This action is not available now");
    }
}
