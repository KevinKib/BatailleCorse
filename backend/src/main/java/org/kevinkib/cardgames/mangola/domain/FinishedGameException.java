package org.kevinkib.cardgames.mangola.domain;

/** The game is over: no command is accepted any more. */
public class FinishedGameException extends MangolaException {

    public FinishedGameException() {
        super("The game is finished");
    }
}
