package org.kevinkib.cardgames.sessionmanagement.application;

import org.kevinkib.cardgames.game.GameId;

public class RoomFullException extends RuntimeException {
    public RoomFullException(GameId id) {
        super("Room " + id + " is full.");
    }
}
