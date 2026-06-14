package org.kevinkib.cardgames.bataillecorse.domain;

public class FinishedGameException extends Exception {

    public FinishedGameException() {
        super("Cannot perform this action on a finished game.");
    }
}
