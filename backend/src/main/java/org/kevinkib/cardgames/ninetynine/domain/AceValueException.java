package org.kevinkib.cardgames.ninetynine.domain;

/** An Ace was played without a value, with a value other than 1 or 11, or at 11 over 99. */
public class AceValueException extends NinetyNineException {

    public AceValueException() {
        super("An Ace needs a value of 1, or 11 when it stays at 99 or below");
    }
}
