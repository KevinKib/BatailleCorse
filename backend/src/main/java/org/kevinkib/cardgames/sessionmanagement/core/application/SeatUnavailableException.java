package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.PlayerId;

public class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(PlayerId playerId) {
        super("Seat " + playerId.id() + " is already claimed.");
    }
}
