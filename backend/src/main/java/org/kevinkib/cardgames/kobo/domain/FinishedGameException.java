package org.kevinkib.cardgames.kobo.domain;


public class FinishedGameException extends KoboException {

    public FinishedGameException() {
        super("The game is finished");
    }
}
