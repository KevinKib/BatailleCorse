package org.kevinkib.cardgames.sessionmanagement.session.application;

import org.kevinkib.cardgames.game.GameId;

public class GameAlreadyStartedException extends RuntimeException {
    public GameAlreadyStartedException(GameId id) {
        super("Game " + id + " has already started.");
    }
}
