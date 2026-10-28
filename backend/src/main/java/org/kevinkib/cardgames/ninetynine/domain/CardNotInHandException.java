package org.kevinkib.cardgames.ninetynine.domain;

/** The card played is not in the seat's hand. */
public class CardNotInHandException extends NinetyNineException {

    public CardNotInHandException() {
        super("That card is not in your hand");
    }
}
