package org.kevinkib.bataillecorse.sessionmanagement.application;

import org.kevinkib.bataillecorse.core.domain.PlayerId;

public class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(PlayerId playerId) {
        super("Seat " + playerId.id() + " is already claimed.");
    }
}
