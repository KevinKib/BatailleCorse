package org.kevinkib.bataillecorse.domain;

public class CannotSlapIfNoCardsInPileException extends Exception {

    public CannotSlapIfNoCardsInPileException() {
        super("Cannot slap if there are no cards in the pile.");
    }

}
