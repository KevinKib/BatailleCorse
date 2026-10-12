package org.kevinkib.cardgames.sessionmanagement.core.domain;

import org.kevinkib.cardgames.game.PlayerId;

/** Raised when a human-only operation targets a seat that is free or held by a bot. */
public class NotAHumanSeatException extends RuntimeException {

    public NotAHumanSeatException(PlayerId seat) {
        super("Seat " + seat.id() + " is not held by a human");
    }
}
