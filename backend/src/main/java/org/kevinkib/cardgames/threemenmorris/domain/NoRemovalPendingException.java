package org.kevinkib.cardgames.threemenmorris.domain;

/** A removal was asked for while no mill is waiting for one. */
public class NoRemovalPendingException extends ThreeMenMorrisException {

    public NoRemovalPendingException() {
        super("There is no piece to remove");
    }
}
