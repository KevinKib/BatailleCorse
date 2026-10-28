package org.kevinkib.cardgames.blackjack.domain;

/** The game is over: no command is accepted. */
public class FinishedGameException extends BlackjackException {

    public FinishedGameException() {
        super("The game is over");
    }
}
