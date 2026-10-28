package org.kevinkib.cardgames.blackjack.domain;

/** The command does not belong to the current phase (a bet while playing, a hit while betting). */
public class WrongPhaseException extends BlackjackException {

    public WrongPhaseException(Phase expected, Phase actual) {
        super("Expected phase " + expected + " but the game is in phase " + actual);
    }
}
