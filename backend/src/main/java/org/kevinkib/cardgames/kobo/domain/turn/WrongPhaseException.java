package org.kevinkib.cardgames.kobo.domain.turn;

import org.kevinkib.cardgames.kobo.domain.rules.KoboException;


public class WrongPhaseException extends KoboException {

    public WrongPhaseException(Phase actual, String expected) {
        super("Not allowed in phase " + actual + " (expected " + expected + ")");
    }

    public WrongPhaseException(String message) {
        super(message);
    }
}
