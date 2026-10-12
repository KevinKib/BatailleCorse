package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.GameId;

/** A computer player was requested in a room of a game that has no bot. */
public class BotsNotSupportedException extends RuntimeException {
    public BotsNotSupportedException(GameId id, String gameType) {
        super("Game " + gameType + " has no computer player (room " + id + ").");
    }
}
