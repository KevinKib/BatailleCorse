package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.GameId;

public class InvalidGameIdException extends RuntimeException {

    public InvalidGameIdException(GameId id) {
        super("The game with id "+id+" does not exist.");
    }
}
