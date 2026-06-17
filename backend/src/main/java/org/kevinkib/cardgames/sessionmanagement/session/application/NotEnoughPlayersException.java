package org.kevinkib.cardgames.sessionmanagement.session.application;

import org.kevinkib.cardgames.game.GameId;

public class NotEnoughPlayersException extends RuntimeException {
    public NotEnoughPlayersException(GameId id, int present, int required) {
        super("Game " + id + " needs " + required + " players to start; only " + present + " present.");
    }
}
