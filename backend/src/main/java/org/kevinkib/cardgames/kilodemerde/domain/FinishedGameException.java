package org.kevinkib.cardgames.kilodemerde.domain;

/** The game is over: no command is accepted any more. */
public class FinishedGameException extends KiloDeMerdeException {

    public FinishedGameException() {
        super("The game is over");
    }
}
