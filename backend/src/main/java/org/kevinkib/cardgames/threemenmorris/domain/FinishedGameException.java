package org.kevinkib.cardgames.threemenmorris.domain;

/** The game is over: no command is accepted any more. */
public class FinishedGameException extends ThreeMenMorrisException {

    public FinishedGameException() {
        super("The game is finished");
    }
}
