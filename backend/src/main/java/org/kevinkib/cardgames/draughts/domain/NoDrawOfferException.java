package org.kevinkib.cardgames.draughts.domain;

/** There is no draw offer to answer. */
public class NoDrawOfferException extends DraughtsException {

    public static final String CODE = "draughts.errors.noDrawOffer";

    public NoDrawOfferException() {
        super(CODE);
    }
}
