package org.kevinkib.cardgames.bullshit.domain;

public class InvalidDiscardCountException extends Exception {

    public InvalidDiscardCountException(int count) {
        super("A discard must contain 1 to 4 cards, got " + count);
    }
}
