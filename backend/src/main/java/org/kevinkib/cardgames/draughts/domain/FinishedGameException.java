package org.kevinkib.cardgames.draughts.domain;

/** The game is over: no command is accepted any more. */
public class FinishedGameException extends DraughtsException {

    public static final String CODE = "draughts.errors.gameFinished";

    public FinishedGameException() {
        super(CODE);
    }
}
