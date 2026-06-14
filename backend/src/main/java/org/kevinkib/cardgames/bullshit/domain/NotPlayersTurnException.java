package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cardgames.bullshit.domain.player.PlayerId;

public class NotPlayersTurnException extends Exception {

    public NotPlayersTurnException(PlayerId playerId) {
        super("Not the turn of player " + playerId);
    }
}
