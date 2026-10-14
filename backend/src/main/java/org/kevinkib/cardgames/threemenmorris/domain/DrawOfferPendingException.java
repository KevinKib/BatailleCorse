package org.kevinkib.cardgames.threemenmorris.domain;

/** A draw offer is already pending: only one at a time. */
public class DrawOfferPendingException extends ThreeMenMorrisException {

    public DrawOfferPendingException() {
        super("A draw offer is already pending");
    }
}
