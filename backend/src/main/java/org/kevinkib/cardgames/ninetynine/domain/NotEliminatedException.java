package org.kevinkib.cardgames.ninetynine.domain;

/** A place was asked for a seat that is still in the game. */
public class NotEliminatedException extends NinetyNineException {

    public NotEliminatedException() {
        super("That player is still in the game");
    }
}
