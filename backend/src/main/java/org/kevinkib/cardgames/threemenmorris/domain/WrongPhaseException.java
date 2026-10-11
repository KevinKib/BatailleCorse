package org.kevinkib.cardgames.threemenmorris.domain;

/** The command does not belong to the current phase (a placement during movement or the reverse). */
public class WrongPhaseException extends ThreeMenMorrisException {

    public WrongPhaseException(Phase expected, Phase actual) {
        super("Expected phase " + expected + " but the game is in " + actual);
    }
}
