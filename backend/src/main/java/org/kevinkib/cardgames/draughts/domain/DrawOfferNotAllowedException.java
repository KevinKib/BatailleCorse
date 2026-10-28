package org.kevinkib.cardgames.draughts.domain;

/**
 * The draw command is not allowed now: a second offer since the seat last moved, or an answer to
 * the seat's own offer.
 */
public class DrawOfferNotAllowedException extends DraughtsException {

    public static final String ALREADY_OFFERED = "draughts.errors.drawAlreadyOffered";
    public static final String OWN_OFFER = "draughts.errors.ownDrawOffer";

    public DrawOfferNotAllowedException(String code) {
        super(code);
    }
}
