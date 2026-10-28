package org.kevinkib.cardgames.kilodemerde.domain;

/** The card is not in the seat's hand. */
public class CardNotInHandException extends KiloDeMerdeException {

    public CardNotInHandException() {
        super("That card is not in your hand");
    }
}
