package org.kevinkib.cardgames.bullshit.domain;

public class NotPlayersTurnException extends Exception {

    public NotPlayersTurnException(PlayerId playerId) {
        super("Not the turn of player " + playerId);
    }
}
