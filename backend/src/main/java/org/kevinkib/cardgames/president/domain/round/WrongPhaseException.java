package org.kevinkib.cardgames.president.domain.round;

import org.kevinkib.cardgames.president.domain.rules.PresidentException;

/** The command does not belong to the phase the game is in. */
public class WrongPhaseException extends PresidentException {

    public WrongPhaseException(Phase actual, String expected) {
        super("Wrong phase: the game is in " + actual + ", this command needs " + expected);
    }
}
