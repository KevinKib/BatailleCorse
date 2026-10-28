package org.kevinkib.cardgames.draughts.domain;

/** A draw offer is already waiting for an answer. */
public class DrawOfferPendingException extends DraughtsException {

    public static final String CODE = "draughts.errors.drawOfferPending";

    public DrawOfferPendingException() {
        super(CODE);
    }
}
