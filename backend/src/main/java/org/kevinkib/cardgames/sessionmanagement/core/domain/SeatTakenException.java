package org.kevinkib.cardgames.sessionmanagement.core.domain;

import org.kevinkib.cardgames.game.PlayerId;

public class SeatTakenException extends RuntimeException {
    public SeatTakenException(PlayerId playerId) {
        super("Seat " + playerId.id() + " is already claimed.");
    }
}
