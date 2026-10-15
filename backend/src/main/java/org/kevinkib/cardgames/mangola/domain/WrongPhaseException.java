package org.kevinkib.cardgames.mangola.domain;

/** The command does not belong to the phase the game is in. */
public class WrongPhaseException extends MangolaException {

    public WrongPhaseException(Phase expected, Phase actual) {
        super("This command needs the " + expected + " phase, the game is in " + actual);
    }
}
