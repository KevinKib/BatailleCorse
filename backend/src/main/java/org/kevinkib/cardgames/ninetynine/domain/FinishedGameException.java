package org.kevinkib.cardgames.ninetynine.domain;

/** The game is over: no command is accepted. */
public class FinishedGameException extends NinetyNineException {

    public FinishedGameException() {
        super("The game is over");
    }
}
