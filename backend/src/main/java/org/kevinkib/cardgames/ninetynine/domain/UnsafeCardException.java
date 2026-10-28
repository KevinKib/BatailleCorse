package org.kevinkib.cardgames.ninetynine.domain;

/** The card would take the total over 99 while the hand holds a safe card. */
public class UnsafeCardException extends NinetyNineException {

    public UnsafeCardException() {
        super("That card goes over 99 and you hold a safe card");
    }
}
