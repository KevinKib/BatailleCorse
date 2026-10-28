package org.kevinkib.cardgames.kilodemerde.domain;

/** The seat has already slapped in this race. */
public class AlreadySlappedException extends KiloDeMerdeException {

    public AlreadySlappedException() {
        super("You have already slapped");
    }
}
