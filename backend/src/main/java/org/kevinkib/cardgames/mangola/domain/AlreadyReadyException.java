package org.kevinkib.cardgames.mangola.domain;

/** The seat has already confirmed its layout: it must take the confirmation back before changing it. */
public class AlreadyReadyException extends MangolaException {

    public AlreadyReadyException() {
        super("The layout is already confirmed");
    }
}
