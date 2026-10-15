package org.kevinkib.cardgames.mangola.domain;

/** The pit belongs to the other side. */
public class NotYourPitException extends MangolaException {

    public NotYourPitException(int pit) {
        super("Pit " + MangolaGeometry.name(pit) + " is not yours");
    }
}
