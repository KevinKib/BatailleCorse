package org.kevinkib.cardgames.mangola.domain;

/** The index is not one of the 32 pits. */
public class InvalidPitException extends MangolaException {

    public InvalidPitException(int pit) {
        super("No such pit: " + pit);
    }
}
