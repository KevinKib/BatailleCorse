package org.kevinkib.cardgames.threemenmorris.domain;

/**
 * The draw command is not allowed now: outside the movement phase, a second offer since the seat's
 * last move, or an answer to the seat's own offer.
 */
public class DrawOfferNotAllowedException extends ThreeMenMorrisException {

    public DrawOfferNotAllowedException(String message) {
        super(message);
    }
}
