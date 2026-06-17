package org.kevinkib.cardgames.sessionmanagement.session.application;

import org.kevinkib.cardgames.game.GameId;

public class NotHostException extends RuntimeException {
    public NotHostException(GameId id) {
        super("Only the host may start game " + id + ".");
    }
}
