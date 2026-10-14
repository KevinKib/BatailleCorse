package org.kevinkib.cardgames.threemenmorris.domain;

/** There is no draw offer to answer. */
public class NoDrawOfferException extends ThreeMenMorrisException {

    public NoDrawOfferException() {
        super("There is no draw offer to answer");
    }
}
