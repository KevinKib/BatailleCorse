package org.kevinkib.cardgames.bullshit.domain;

public class CannotCallBullshitException extends Exception {

    public CannotCallBullshitException(PlayerId playerId) {
        super("Player " + playerId + " cannot call bullshit now");
    }
}
