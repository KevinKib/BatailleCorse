package org.kevinkib.cardgames.bataillecorse.domain;

public class CannotSlapIfNoCardsInPileException extends Exception {

    public CannotSlapIfNoCardsInPileException() {
        super("Cannot slap if there are no cards in the pile.");
    }

}
