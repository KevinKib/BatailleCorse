package org.kevinkib.cardgames.blackjack.domain;

/** The seat tried to act while another seat is on turn. */
public class NotPlayersTurnException extends BlackjackException {

    public NotPlayersTurnException() {
        super("It is not this player's turn");
    }
}
