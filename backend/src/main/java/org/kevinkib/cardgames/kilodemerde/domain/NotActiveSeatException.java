package org.kevinkib.cardgames.kilodemerde.domain;

/** The seat is not part of the table (unknown, or it has left). */
public class NotActiveSeatException extends KiloDeMerdeException {

    public NotActiveSeatException() {
        super("You are not at this table");
    }
}
