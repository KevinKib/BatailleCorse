package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cardgames.game.PlayerId;

public class CannotCallBullshitException extends Exception {

    public CannotCallBullshitException(PlayerId playerId) {
        super("Player " + playerId + " cannot call bullshit now");
    }
}
