package org.kevinkib.cardgames.mangola.domain;

/** The seat has no confirmation to take back. */
public class NotReadyException extends MangolaException {

    public NotReadyException() {
        super("The layout is not confirmed");
    }
}
