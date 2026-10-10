package org.kevinkib.cardgames.sessionmanagement.core.domain;

import org.kevinkib.cardgames.game.PlayerId;

/** Raised when a bot-only operation targets a seat that is free or held by a human. */
public class NotABotSeatException extends RuntimeException {

    public NotABotSeatException(PlayerId seat) {
        super("Seat " + seat.id() + " is not held by a bot");
    }
}
