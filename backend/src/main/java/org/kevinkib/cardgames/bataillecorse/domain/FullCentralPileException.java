package org.kevinkib.cardgames.bataillecorse.domain;

public class FullCentralPileException extends Exception {

    public FullCentralPileException() {
        super("Cannot add any cards in the central pile as it is full, without clearing it first.");
    }

}
