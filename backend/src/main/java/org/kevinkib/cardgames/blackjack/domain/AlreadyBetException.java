package org.kevinkib.cardgames.blackjack.domain;

/** The seat already placed its bet for this hand; a bet cannot be changed. */
public class AlreadyBetException extends BlackjackException {

    public AlreadyBetException() {
        super("This player already bet on this hand");
    }
}
