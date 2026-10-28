package org.kevinkib.cardgames.kilodemerde.domain;

/** The seat has already chosen its card for this step; a choice is final. */
public class AlreadyChoseException extends KiloDeMerdeException {

    public AlreadyChoseException() {
        super("You have already chosen a card for this step");
    }
}
