package org.kevinkib.cardgames.sessionmanagement.core.domain;

import org.kevinkib.cardgames.game.GameId;

public class NoFreeSeatException extends RuntimeException {
    public NoFreeSeatException(GameId id) {
        super("Room " + id + " is full.");
    }
}
